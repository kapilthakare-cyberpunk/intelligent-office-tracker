package com.office.tracker.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.office.tracker.OfficeApp
import com.office.tracker.db.VisitRepository
import com.office.tracker.util.Prefs
import com.office.tracker.util.formatHHmm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Foreground location service that runs only inside the arrival/departure
 * windows. Window transition logic lives in [WindowStateMachine] (pure, tested);
 * this class only feeds it locations and applies the emitted events to the DB.
 */
class OfficeTrackingService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: VisitRepository
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    private var activeWindow: WindowType? = null
    private var windowState = WindowState()
    private val stateMachine = WindowStateMachine()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = VisitRepository(OfficeApp.instance.database.officeVisitDao())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val windowType = WindowType.fromWire(intent?.getStringExtra(WindowType.EXTRA_WINDOW_TYPE))
        Log.d(TAG, "onStartCommand: action=$action window=${windowType?.wire}")

        startForegroundWithNotification("Office Tracker active")

        when (action) {
            WindowScheduler.ACTION_WINDOW_START -> windowType?.let { startWindow(it) }
            WindowScheduler.ACTION_WINDOW_END -> windowType?.let { endWindow(it) }
            ACTION_STOP -> {
                stopLocationUpdates()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startWindow(windowType: WindowType) {
        activeWindow = windowType
        windowState = WindowState()
        Log.d(TAG, "Starting ${windowType.wire} window")
        updateNotification("Tracking: ${windowType.wire} window active")

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    scope.launch { handleFix(loc) }
                }
            }
        }

        try {
            fusedLocationClient?.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "No location permission", e)
            updateNotification("Location permission missing!")
        }
    }

    private fun endWindow(windowType: WindowType) {
        Log.d(TAG, "Ending ${windowType.wire} window")

        if (windowType == WindowType.DEPARTURE && activeWindow == WindowType.DEPARTURE &&
            !windowState.hasLoggedDeparture
        ) {
            val today = LocalDate.now().toString()
            scope.launch {
                val visit = repository.visitForDateOnce(today)
                if (visit != null && visit.isCurrentlyAtOffice) {
                    val now = System.currentTimeMillis()
                    repository.recordDeparture(today, formatHHmm(now), now)
                    Log.d(TAG, "Logged departure at window end")
                }
            }
        }

        stopLocationUpdates()
        activeWindow = null
        updateNotification("Office Tracker standby")

        // Schedule tomorrow + integrity + persist plan (self-heal after the last window).
        if (windowType == WindowType.DEPARTURE) {
            scope.launch { WindowScheduler.rearm(this@OfficeTrackingService, allowInlineStart = false) }
        }
    }

    private suspend fun handleFix(location: Location) {
        val today = LocalDate.now().toString()
        val officeLat = Prefs.getOfficeLat(this)
        val officeLng = Prefs.getOfficeLng(this)
        val radius = Prefs.getOfficeRadius(this)

        val dist = FloatArray(1)
        Location.distanceBetween(
            officeLat, officeLng, location.latitude, location.longitude, dist
        )
        val isAtOffice = dist[0] <= radius
        val window = activeWindow ?: return

        val visit = repository.visitForDateOnce(today)
        val now = System.currentTimeMillis()
        val (newState, event) = stateMachine.onLocation(window, isAtOffice, visit, windowState, now)
        windowState = newState

        when (event) {
            is WindowEvent.LogArrival -> {
                repository.recordArrival(today, formatHHmm(now), now)
                Log.d(TAG, "ARRIVAL logged (${dist[0].toInt()}m from office)")
                updateNotification("At office since ${formatHHmm(now)}")
            }
            is WindowEvent.LogDeparture -> {
                repository.recordDeparture(today, formatHHmm(now), now)
                Log.d(TAG, "DEPARTURE logged")
                updateNotification("Left office at ${formatHHmm(now)}")
            }
            is WindowEvent.ReArrival -> {
                repository.recordReArrival(today, formatHHmm(now), now)
                Log.d(TAG, "RE-ARRIVAL logged")
                updateNotification("Back at office since ${formatHHmm(now)}")
            }
            WindowEvent.Noop -> Unit
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    private fun startForegroundWithNotification(text: String) {
        val notification = buildNotification(text)
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.office.tracker.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OfficeTrackingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, OfficeApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Office Tracker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OfficeTracker"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.office.tracker.STOP"
    }
}
