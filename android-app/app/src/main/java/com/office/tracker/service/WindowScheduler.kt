package com.office.tracker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.office.tracker.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Schedules two daily windows:
 *   - Arrival window: checks location to detect when user reaches office
 *   - Departure window: checks location to detect when user leaves office
 *
 * Reliability design (see README):
 *   - Uses AlarmManager for exact timing.
 *   - Weekday-aware: non-work days are skipped so we never pester on off days.
 *   - Redundant: each arrival window also gets a setAlarmClock (highest priority,
 *     exempt from Doze) in addition to the exact alarm, plus the service itself
 *     is START_STICKY, so one backup can replace a lost alarm.
 *   - Integrity checks are scheduled through the day to catch missed days.
 *   - `ensureArmed` re-schedules from app open / boot / integrity sweep so a
 *     killed schedule self-heals.
 */
object WindowScheduler {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    const val ACTION_WINDOW_START = "com.office.tracker.WINDOW_START"
    const val ACTION_WINDOW_END = "com.office.tracker.WINDOW_END"
    const val EXTRA_WINDOW_TYPE = "window_type"
    const val WINDOW_ARRIVAL = "arrival"
    const val WINDOW_DEPARTURE = "departure"

    // Integrity check actions
    const val ACTION_INTEGRITY_ARRIVAL = "com.office.tracker.ARRIVAL_CHECK"
    const val ACTION_INTEGRITY_DEPARTURE = "com.office.tracker.DEPARTURE_CHECK"

    private const val REQ_ARRIVAL_START = 0
    private const val REQ_ARRIVAL_END = 1
    private const val REQ_DEPARTURE_START = 2
    private const val REQ_DEPARTURE_END = 3
    private const val REQ_ARRIVAL_CHECK = 10
    private const val REQ_DEPARTURE_CHECK = 11

    fun scheduleToday(context: Context) {
        // Cancel then schedule inside a coroutine (work-day read is suspend).
        val now = Calendar.getInstance()
        cancelAll(context)
        scope.launch {
            val aStart = Prefs.getArrivalWindowStart(context)
            val aEnd = Prefs.getArrivalWindowEnd(context)
            val dStart = Prefs.getDepartureWindowStart(context)
            val dEnd = Prefs.getDepartureWindowEnd(context)

            scheduleWindowSet(context, now, offsetDays = 0,
                arrivalStartH = aStart, arrivalEndH = aEnd,
                departureStartH = dStart, departureEndH = dEnd)

            // If we are already INSIDE an active window right now, kick off the
            // tracking service immediately. This is the reliable path on
            // Android 12+ where a background alarm can't start a foreground
            // service — e.g. the user opens the app after 9am, so the 9am
            // setAlarmClock exemption is in the past and would otherwise never
            // fire, leaving today un-tracked (no auto punch-in/out).
            if (Prefs.isWorkDay(context, now.get(Calendar.DAY_OF_WEEK))) {
                val hour = now.get(Calendar.HOUR_OF_DAY)
                val windowType =
                    if (hour >= aStart && hour < aEnd) WINDOW_ARRIVAL
                    else if (hour >= dStart && hour < dEnd) WINDOW_DEPARTURE
                    else null
                if (windowType != null) {
                    Log.d(TAG, "scheduleToday: inside $windowType window; starting service now")
                    startTrackingService(context, windowType)
                }
            }
        }
        scheduleIntegrityChecks(context)
    }

    fun scheduleTomorrow(context: Context) {
        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        scope.launch {
            val aStart = Prefs.getArrivalWindowStart(context)
            val aEnd = Prefs.getArrivalWindowEnd(context)
            val dStart = Prefs.getDepartureWindowStart(context)
            val dEnd = Prefs.getDepartureWindowEnd(context)
            scheduleWindowSet(context, tomorrow, offsetDays = 1,
                arrivalStartH = aStart, arrivalEndH = aEnd,
                departureStartH = dStart, departureEndH = dEnd)
        }
    }

    /**
     * Starts the foreground tracking service for the given window.
     * Safe to call from any context (app open, boot, receiver).
     */
    fun startTrackingService(context: Context, windowType: String) {
        val serviceIntent = Intent(context, OfficeTrackingService::class.java).apply {
            action = ACTION_WINDOW_START
            putExtra(EXTRA_WINDOW_TYPE, windowType)
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground service: ${e.message}")
        }
    }

    /**
     * Schedules the arrival/departure window boundaries for a given base day,
     * skipping entirely if that day is not a work day. Fire-and-forget: launches
     * a background coroutine because the work-day setting is a suspend DataStore read.
     */
    private fun scheduleWindowSet(
        context: Context,
        base: Calendar,
        offsetDays: Int,
        arrivalStartH: Int, arrivalEndH: Int,
        departureStartH: Int, departureEndH: Int
    ) {
        scope.launch {
            if (!Prefs.isWorkDay(context, base.get(Calendar.DAY_OF_WEEK))) {
                Log.d(TAG, "Skipping windows for non-work day (dow=${base.get(Calendar.DAY_OF_WEEK)})")
                return@launch
            }

            val arrivalStart = atHour(base, arrivalStartH)
            val arrivalEnd = atHour(base, arrivalEndH)
            val departureStart = atHour(base, departureStartH)
            val departureEnd = atHour(base, departureEndH)

            // Arrival start: exact alarm + setAlarmClock (redundant backup / Doze-proof)
            scheduleAlarm(context, arrivalStart, ACTION_WINDOW_START, WINDOW_ARRIVAL, REQ_ARRIVAL_START)
            scheduleAlarmClock(context, arrivalStart, "Office Tracker: arrival window")

            scheduleAlarm(context, arrivalEnd, ACTION_WINDOW_END, WINDOW_ARRIVAL, REQ_ARRIVAL_END)
            scheduleAlarm(context, departureStart, ACTION_WINDOW_START, WINDOW_DEPARTURE, REQ_DEPARTURE_START)
            scheduleAlarm(context, departureEnd, ACTION_WINDOW_END, WINDOW_DEPARTURE, REQ_DEPARTURE_END)

            Log.d(TAG, "Windows scheduled for offsetDay=$offsetDays "
                + "arr=${arrivalStart.get(Calendar.HOUR_OF_DAY)}:00-${arrivalEnd.get(Calendar.HOUR_OF_DAY)}:00 "
                + "dep=${departureStart.get(Calendar.HOUR_OF_DAY)}:00-${departureEnd.get(Calendar.HOUR_OF_DAY)}:00")
        }
    }

    private fun atHour(base: Calendar, hour: Int): Calendar =
        (base.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    /**
     * Schedules the missed-day integrity checks for today.
     *   - arrival check: shortly after the arrival window end (13:00 default)
     *   - departure check: shortly after the departure window end (22:00 default)
     * These are re-armed opportunistically every time the service starts and on
     * app open, so even if one is lost the next day's are re-planned.
     */
    fun scheduleIntegrityChecks(context: Context) {
        val now = Calendar.getInstance()
        val arrivalCheck = atHour(now, 13)
        val departureCheck = atHour(now, 22)

        scheduleAlarm(context, arrivalCheck, ACTION_INTEGRITY_ARRIVAL, IntegrityCheckReceiver.TYPE_ARRIVAL, REQ_ARRIVAL_CHECK)
        scheduleAlarm(context, departureCheck, ACTION_INTEGRITY_DEPARTURE, IntegrityCheckReceiver.TYPE_DEPARTURE, REQ_DEPARTURE_CHECK)
    }

    private fun scheduleAlarm(
        context: Context,
        triggerAt: Calendar,
        action: String,
        windowType: String,
        requestCode: Int
    ) {
        val intent = Intent(context, WindowAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_WINDOW_TYPE, windowType)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt.timeInMillis,
            pendingIntent
        )
    }

    private fun scheduleAlarmClock(context: Context, triggerAt: Calendar, label: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        // Only meaningful for the arrival window start; skip if in the past.
        if (triggerAt.timeInMillis <= System.currentTimeMillis()) return

        val showIntent = Intent(context, WindowAlarmReceiver::class.java).apply {
            action = ACTION_WINDOW_START
            putExtra(EXTRA_WINDOW_TYPE, WINDOW_ARRIVAL)
        }
        val pendingShowIntent = PendingIntent.getBroadcast(
            context, 99, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val editIntent = Intent(context, WindowAlarmReceiver::class.java).apply {
            action = ACTION_WINDOW_START
            putExtra(EXTRA_WINDOW_TYPE, WINDOW_ARRIVAL)
        }
        val pendingEditIntent = PendingIntent.getBroadcast(
            context, 98, editIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt.timeInMillis, pendingShowIntent),
            pendingEditIntent
        )
    }

    /**
     * Self-heal: if the earliest scheduled window is already in the past (i.e.
     * the schedule died), re-arm it. Called on app open, boot, and integrity checks.
     */
    fun ensureArmed(context: Context) {
        val now = System.currentTimeMillis()

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val nextClock = alarmManager.nextAlarmClock?.triggerTime ?: 0L
        if (nextClock == 0L || nextClock < now) {
            Log.d(TAG, "ensureArmed: schedule appears missing; re-scheduling")
            scheduleToday(context)
            scheduleTomorrow(context)
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val items = listOf(
            0 to ACTION_WINDOW_START, 1 to ACTION_WINDOW_END,
            2 to ACTION_WINDOW_START, 3 to ACTION_WINDOW_END,
            REQ_ARRIVAL_CHECK to ACTION_INTEGRITY_ARRIVAL,
            REQ_DEPARTURE_CHECK to ACTION_INTEGRITY_DEPARTURE
        )
        for ((req, action) in items) {
            val intent = Intent(context, WindowAlarmReceiver::class.java).apply { this.action = action }
            val pendingIntent = PendingIntent.getBroadcast(
                context, req, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    private const val TAG = "WindowScheduler"
}
