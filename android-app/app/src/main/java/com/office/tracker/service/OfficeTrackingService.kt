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
import com.google.android.gms.location.*
import com.office.tracker.OfficeApp
import com.office.tracker.db.OfficeVisit
import com.office.tracker.ui.MainActivity
import com.office.tracker.util.Prefs
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class OfficeTrackingService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    private var activeWindow: String? = null // "arrival" or "departure"
    private var hasLoggedArrival = false
    private var hasLoggedDeparture = false

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val windowType = intent?.getStringExtra(WindowScheduler.EXTRA_WINDOW_TYPE)

        Log.d(TAG, "onStartCommand: action=$action, window=$windowType")

        // Always start as foreground immediately
        startForegroundWithNotification("Office Tracker active")

        when (action) {
            WindowScheduler.ACTION_WINDOW_START -> {
                startWindow(windowType ?: return START_STICKY)
            }
            WindowScheduler.ACTION_WINDOW_END -> {
                endWindow(windowType ?: return START_STICKY)
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY // Restart if killed
    }

    private fun startWindow(windowType: String) {
        activeWindow = windowType
        hasLoggedArrival = false
        hasLoggedDeparture = false

        Log.d(TAG, "Starting $windowType window")

        updateNotification("Tracking: $windowType window active")

        // Start location updates
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { checkProximity(it) }
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

    private fun endWindow(windowType: String) {
        Log.d(TAG, "Ending $windowType window")

        // If we were at office and haven't logged departure yet, do it now
        if (windowType == "departure" && activeWindow == "departure") {
            // The last known location check already handled departure logging
            // But if user was still at office when window ended, log departure at window end time
            if (!hasLoggedDeparture) {
                scope.launch {
                    val today = dateFmt.format(Date())
                    val visit = OfficeApp.instance.database.officeVisitDao().getVisitForDate(today)
                    if (visit != null && visit.isCurrentlyAtOffice) {
                        val now = timeFmt.format(Date())
                        OfficeApp.instance.database.officeVisitDao()
                            .setDeparture(today, now, System.currentTimeMillis())
                        Log.d(TAG, "Logged departure at window end: $now")
                    }
                }
            }
        }

        stopLocationUpdates()
        activeWindow = null

        // Schedule tomorrow's windows if this was the last window of the day
        if (windowType == "departure") {
            WindowScheduler.scheduleTomorrow(this)
        }

        updateNotification("Office Tracker standby")
    }

    private fun checkProximity(location: Location) {
        scope.launch {
            val officeLat = Prefs.getOfficeLat(this@OfficeTrackingService)
            val officeLng = Prefs.getOfficeLng(this@OfficeTrackingService)
            val radius = Prefs.getOfficeRadius(this@OfficeTrackingService)

            val officeLocation = Location("office").apply {
                latitude = officeLat
                longitude = officeLng
            }

            val distance = location.distanceTo(officeLocation)
            val isAtOffice = distance <= radius

            val today = dateFmt.format(Date())
            val now = timeFmt.format(Date())
            val nowMillis = System.currentTimeMillis()
            val dao = OfficeApp.instance.database.officeVisitDao()

            when (activeWindow) {
                "arrival" -> {
                    if (isAtOffice && !hasLoggedArrival) {
                        hasLoggedArrival = true
                        val existing = dao.getVisitForDate(today)
                        if (existing == null) {
                            dao.upsert(
                                OfficeVisit(
                                    date = today,
                                    arrivalTime = now,
                                    arrivalTimestamp = nowMillis,
                                    isCurrentlyAtOffice = true
                                )
                            )
                        } else if (existing.arrivalTime == null) {
                            dao.setArrival(today, now, nowMillis)
                        }
                        Log.d(TAG, "ARRIVAL logged at $now (${distance.toInt()}m from office)")
                        updateNotification("At office since $now")
                    } else if (!isAtOffice && hasLoggedArrival) {
                        // Left office during arrival window - mark departure
                        hasLoggedArrival = false
                        dao.setDeparture(today, now, nowMillis)
                        Log.d(TAG, "DEPARTURE logged during arrival window at $now")
                    }
                }
                "departure" -> {
                    if (!isAtOffice && !hasLoggedDeparture) {
                        // Just left office
                        val existing = dao.getVisitForDate(today)
                        if (existing != null && existing.isCurrentlyAtOffice) {
                            hasLoggedDeparture = true
                            dao.setDeparture(today, now, nowMillis)
                            Log.d(TAG, "DEPARTURE logged at $now (${distance.toInt()}m from office)")
                            updateNotification("Left office at $now")
                        }
                    } else if (isAtOffice && hasLoggedDeparture) {
                        // Came back during departure window
                        hasLoggedDeparture = false
                        hasLoggedArrival = true
                        dao.setArrival(today, now, nowMillis)
                        Log.d(TAG, "RE-ARRIVAL logged at $now")
                        updateNotification("Back at office since $now")
                    }
                }
            }
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
            Intent(this, MainActivity::class.java),
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
            .addAction(
                Notification.Action.Builder(
                    null, "Stop", stopIntent
                ).build()
            )
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
