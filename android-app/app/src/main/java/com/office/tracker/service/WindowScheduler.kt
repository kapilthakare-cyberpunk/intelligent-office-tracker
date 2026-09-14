package com.office.tracker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.office.tracker.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Owns the daily window alarms + integrity sweeps.
 *
 * Reliability rules (post-refactor):
 *  - Every day gets UNIQUE PendingIntent request codes ([AlarmIds]), so today's
 *    and tomorrow's alarms can coexist (previously tomorrow's replaced today's).
 *  - `rearm()` is the single self-heal entry point: cancel -> schedule today &
 *    tomorrow windows -> schedule integrity checks -> persist the next planned
 *    window. Called on app open, boot, every integrity sweep and departure end.
 *  - Past alarms are never re-armed (they would fire instantly); the inline
 *    "inside a window right now" check starts the service directly instead.
 *  - A coroutine [rearmMutex] prevents two concurrent re-arms (app open + boot)
 *    from racing cancel/schedule and losing alarms.
 */
object WindowScheduler {

    const val ACTION_WINDOW_START = "com.office.tracker.WINDOW_START"
    const val ACTION_WINDOW_END = "com.office.tracker.WINDOW_END"
    const val ACTION_INTEGRITY_ARRIVAL = "com.office.tracker.ARRIVAL_CHECK"
    const val ACTION_INTEGRITY_DEPARTURE = "com.office.tracker.DEPARTURE_CHECK"

    const val REQ_INTEGRITY_ARRIVAL = 10
    const val REQ_INTEGRITY_DEPARTURE = 11

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rearmMutex = Mutex()

    val EXTRA_WINDOW_TYPE: String get() = WindowType.EXTRA_WINDOW_TYPE

    fun windowTypeOf(intent: Intent): WindowType? =
        WindowType.fromWire(intent.getStringExtra(WindowType.EXTRA_WINDOW_TYPE))

    /** Non-suspending entry point used by app open, boot and receiveers. */
    fun ensureLatest(context: Context) {
        scope.launch { rearm(context) }
    }

    /**
     * Full re-arm. Safe to call any number of times; never arms past alarms.
     */
    suspend fun rearm(context: Context, allowInlineStart: Boolean = true) {
        rearmMutex.withLock {
            val appCtx = context.applicationContext
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            cancelAll(appCtx)

            val aStart = Prefs.getArrivalWindowStart(appCtx)
            val aEnd = Prefs.getArrivalWindowEnd(appCtx)
            val dStart = Prefs.getDepartureWindowStart(appCtx)
            val dEnd = Prefs.getDepartureWindowEnd(appCtx)

            if (Prefs.isWorkDay(appCtx, today.dayOfWeek)) {
                scheduleDay(appCtx, today, aStart, aEnd, dStart, dEnd)
            } else {
                Log.d(TAG, "Today is not a work day; no window alarms")
            }
            if (Prefs.isWorkDay(appCtx, tomorrow.dayOfWeek)) {
                scheduleDay(appCtx, tomorrow, aStart, aEnd, dStart, dEnd)
            }

            scheduleIntegrityChecks(appCtx, aEnd, dEnd)

            if (allowInlineStart && Prefs.isWorkDay(appCtx, today.dayOfWeek)) {
                val now = LocalTime.now()
                if (now.isAfter(LocalTime.of(aStart, 0)) && now.isBefore(LocalTime.of(aEnd, 0))) {
                    Log.d(TAG, "Inside arrival window; starting service now")
                    startTrackingService(appCtx, WindowType.ARRIVAL)
                } else if (now.isAfter(LocalTime.of(dStart, 0)) && now.isBefore(LocalTime.of(dEnd, 0))) {
                    Log.d(TAG, "Inside departure window; starting service now")
                    startTrackingService(appCtx, WindowType.DEPARTURE)
                }
            }

            persistPlan(appCtx, today, tomorrow, aStart, aEnd, dStart, dEnd)
        }
    }

    fun startTrackingService(context: Context, windowType: WindowType) {
        val intent = Intent(context, OfficeTrackingService::class.java).apply {
            action = ACTION_WINDOW_START
            putExtra(WindowType.EXTRA_WINDOW_TYPE, windowType.wire)
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground service: ${e.message}")
        }
    }

    private fun scheduleDay(
        context: Context,
        date: LocalDate,
        aStart: Int, aEnd: Int,
        dStart: Int, dEnd: Int
    ) {
        val epochDay = date.toEpochDay()
        val now = System.currentTimeMillis()

        scheduleIfFuture(
            context, dateTime(date, aStart), ACTION_WINDOW_START, WindowType.ARRIVAL,
            AlarmIds.requestCode(epochDay, AlarmIds.SLOT_ARRIVAL_START), now
        )
        scheduleIfFuture(
            context, dateTime(date, aEnd), ACTION_WINDOW_END, WindowType.ARRIVAL,
            AlarmIds.requestCode(epochDay, AlarmIds.SLOT_ARRIVAL_END), now
        )
        scheduleIfFuture(
            context, dateTime(date, dStart), ACTION_WINDOW_START, WindowType.DEPARTURE,
            AlarmIds.requestCode(epochDay, AlarmIds.SLOT_DEPARTURE_START), now
        )
        scheduleIfFuture(
            context, dateTime(date, dEnd), ACTION_WINDOW_END, WindowType.DEPARTURE,
            AlarmIds.requestCode(epochDay, AlarmIds.SLOT_DEPARTURE_END), now
        )

        // Redundant alarm-clock (Doze-exempt) for the arrival start only.
        val arrivalStart = dateTime(date, aStart).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (arrivalStart > now) {
            scheduleAlarmClock(
                context, arrivalStart,
                AlarmIds.requestCode(epochDay, AlarmIds.SLOT_CLOCK_SHOW),
                AlarmIds.requestCode(epochDay, AlarmIds.SLOT_CLOCK_EDIT)
            )
        }

        Log.d(TAG, "Windows scheduled for $date: arr ${aStart}:00-${aEnd}:00, dep ${dStart}:00-${dEnd}:00")
    }

    private fun dateTime(date: LocalDate, hour: Int): LocalDateTime = date.atTime(hour, 0)

    private fun scheduleIfFuture(
        context: Context,
        triggerAt: LocalDateTime,
        action: String,
        type: WindowType,
        requestCode: Int,
        nowMillis: Long
    ) {
        val at = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (at <= nowMillis) return
        val intent = Intent(context, WindowAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(WindowType.EXTRA_WINDOW_TYPE, type.wire)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val manager = context.getSystemService(AlarmManager::class.java)
        setExact(manager, at, pi)
    }

    /**
     * Schedule an exact alarm, degrading to a near-exact window when the user
     * hasn't granted exact-alarm access (or revoked it) on Android 12+.
     */
    private fun setExact(manager: AlarmManager, at: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            manager.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000L, pi)
            return
        }
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            manager.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000L, pi)
        }
    }

    private fun scheduleAlarmClock(context: Context, triggerAtMillis: Long, showCode: Int, editCode: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val showIntent = Intent(context, WindowAlarmReceiver::class.java).apply {
            action = ACTION_WINDOW_START
            putExtra(WindowType.EXTRA_WINDOW_TYPE, WindowType.ARRIVAL.wire)
        }
        val editIntent = Intent(context, WindowAlarmReceiver::class.java).apply {
            action = ACTION_WINDOW_START
            putExtra(WindowType.EXTRA_WINDOW_TYPE, WindowType.ARRIVAL.wire)
        }
        val showPi = PendingIntent.getBroadcast(
            context, showCode, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val editPi = PendingIntent.getBroadcast(
            context, editCode, editIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showPi),
                editPi
            )
        } catch (e: SecurityException) {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 60_000L, editPi)
        }
    }

    /**
     * Integrity sweeps shortly after each window ends, derived from the
     * configured window hours instead of hardcoded 13:00/22:00.
     * Past times are skipped (the next rearm covers the next day).
     */
    private suspend fun scheduleIntegrityChecks(context: Context, aEnd: Int, dEnd: Int) {
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val aCheck = dateTime(today, Integer.min(aEnd + 1, 23))
        val dCheck = dateTime(today, Integer.min(dEnd + 1, 23))
        scheduleIfFuture(
            context, aCheck, ACTION_INTEGRITY_ARRIVAL, WindowType.ARRIVAL,
            AlarmIds.requestCode(today.toEpochDay(), 5), now
        )
        scheduleIfFuture(
            context, dCheck, ACTION_INTEGRITY_DEPARTURE, WindowType.DEPARTURE,
            AlarmIds.requestCode(today.toEpochDay(), 6), now
        )
    }

    private suspend fun persistPlan(
        context: Context,
        today: LocalDate,
        tomorrow: LocalDate,
        aStart: Int, aEnd: Int,
        dStart: Int, dEnd: Int
    ) {
        val now = LocalDateTime.now()
        val candidates = mutableListOf<Pair<LocalDateTime, WindowType>>()
        fun addDay(date: LocalDate, isWork: Boolean) {
            if (!isWork) return
            candidates += dateTime(date, aStart) to WindowType.ARRIVAL
            candidates += dateTime(date, aEnd) to WindowType.ARRIVAL
            candidates += dateTime(date, dStart) to WindowType.DEPARTURE
            candidates += dateTime(date, dEnd) to WindowType.DEPARTURE
        }
        addDay(today, Prefs.isWorkDay(context, today.dayOfWeek))
        addDay(tomorrow, Prefs.isWorkDay(context, tomorrow.dayOfWeek))
        val next = candidates.filter { it.first.isAfter(now) }.minByOrNull { it.first }
        if (next != null) {
            val millis = next.first.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            Prefs.setNextPlan(context, millis, next.second.wire)
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val today = LocalDate.now().toEpochDay()
        for (day in today - 1..today + 1) {
            for (slot in intArrayOf(
                AlarmIds.SLOT_ARRIVAL_START, AlarmIds.SLOT_ARRIVAL_END,
                AlarmIds.SLOT_DEPARTURE_START, AlarmIds.SLOT_DEPARTURE_END, 5, 6,
                AlarmIds.SLOT_CLOCK_SHOW, AlarmIds.SLOT_CLOCK_EDIT
            )) {
                val code = AlarmIds.requestCode(day, slot)
                val (action, type) = when (slot) {
                    AlarmIds.SLOT_ARRIVAL_START -> ACTION_WINDOW_START to WindowType.ARRIVAL
                    AlarmIds.SLOT_ARRIVAL_END -> ACTION_WINDOW_END to WindowType.ARRIVAL
                    AlarmIds.SLOT_DEPARTURE_START -> ACTION_WINDOW_START to WindowType.DEPARTURE
                    AlarmIds.SLOT_DEPARTURE_END -> ACTION_WINDOW_END to WindowType.DEPARTURE
                    5 -> ACTION_INTEGRITY_ARRIVAL to WindowType.ARRIVAL
                    6 -> ACTION_INTEGRITY_DEPARTURE to WindowType.DEPARTURE
                    else -> ACTION_WINDOW_START to WindowType.ARRIVAL
                }
                val intent = Intent(context, WindowAlarmReceiver::class.java).apply {
                    this.action = action
                    putExtra(WindowType.EXTRA_WINDOW_TYPE, type.wire)
                }
                val pi = PendingIntent.getBroadcast(
                    context, code, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pi)
            }
        }
        // Legacy fixed codes from pre-refactor builds:
        val legacy = listOf(
            AlarmIds.LEGACY_ARRIVAL_START to (ACTION_WINDOW_START to WindowType.ARRIVAL),
            AlarmIds.LEGACY_ARRIVAL_END to (ACTION_WINDOW_END to WindowType.ARRIVAL),
            AlarmIds.LEGACY_DEPARTURE_START to (ACTION_WINDOW_START to WindowType.DEPARTURE),
            AlarmIds.LEGACY_DEPARTURE_END to (ACTION_WINDOW_END to WindowType.DEPARTURE),
            AlarmIds.LEGACY_ARRIVAL_CHECK to (ACTION_INTEGRITY_ARRIVAL to WindowType.ARRIVAL),
            AlarmIds.LEGACY_DEPARTURE_CHECK to (ACTION_INTEGRITY_DEPARTURE to WindowType.DEPARTURE),
            AlarmIds.LEGACY_CLOCK_SHOW to (ACTION_WINDOW_START to WindowType.ARRIVAL),
            AlarmIds.LEGACY_CLOCK_EDIT to (ACTION_WINDOW_START to WindowType.ARRIVAL)
        )
        for ((code, spec) in legacy) {
            val intent = Intent(context, WindowAlarmReceiver::class.java).apply {
                action = spec.first
                putExtra(WindowType.EXTRA_WINDOW_TYPE, spec.second.wire)
            }
            val pi = PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        }
    }

    private const val TAG = "WindowScheduler"
}
