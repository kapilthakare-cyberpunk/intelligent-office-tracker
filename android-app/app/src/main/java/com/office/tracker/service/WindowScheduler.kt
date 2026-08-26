package com.office.tracker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.office.tracker.util.Prefs
import java.util.Calendar

/**
 * Schedules two daily windows:
 *   - Arrival window: checks location to detect when user reaches office
 *   - Departure window: checks location to detect when user leaves office
 *
 * Uses AlarmManager for exact timing. If the service gets killed,
 * the next alarm will re-trigger it.
 */
object WindowScheduler {

    const val ACTION_WINDOW_START = "com.office.tracker.WINDOW_START"
    const val ACTION_WINDOW_END = "com.office.tracker.WINDOW_END"
    const val EXTRA_WINDOW_TYPE = "window_type"
    const val WINDOW_ARRIVAL = "arrival"
    const val WINDOW_DEPARTURE = "departure"

    fun scheduleToday(context: Context) {
        cancelAll(context)

        val now = Calendar.getInstance()
        val arrivalStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9) // default, will be overridden by prefs
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val arrivalEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val departureStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val departureEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Schedule window starts and ends
        scheduleAlarm(context, arrivalStart, ACTION_WINDOW_START, WINDOW_ARRIVAL, 0)
        scheduleAlarm(context, arrivalEnd, ACTION_WINDOW_END, WINDOW_ARRIVAL, 1)
        scheduleAlarm(context, departureStart, ACTION_WINDOW_START, WINDOW_DEPARTURE, 2)
        scheduleAlarm(context, departureEnd, ACTION_WINDOW_END, WINDOW_DEPARTURE, 3)
    }

    fun scheduleTomorrow(context: Context) {
        // Advance all windows by 1 day
        cancelAll(context)

        val arrivalStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val arrivalEnd = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val departureStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val departureEnd = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        scheduleAlarm(context, arrivalStart, ACTION_WINDOW_START, WINDOW_ARRIVAL, 0)
        scheduleAlarm(context, arrivalEnd, ACTION_WINDOW_END, WINDOW_ARRIVAL, 1)
        scheduleAlarm(context, departureStart, ACTION_WINDOW_START, WINDOW_DEPARTURE, 2)
        scheduleAlarm(context, departureEnd, ACTION_WINDOW_END, WINDOW_DEPARTURE, 3)
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

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        for (i in 0..3) {
            val intent = Intent(context, WindowAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, i, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
