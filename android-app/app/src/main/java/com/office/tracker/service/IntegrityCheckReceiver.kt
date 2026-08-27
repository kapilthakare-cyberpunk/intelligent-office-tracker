package com.office.tracker.service

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.office.tracker.OfficeApp
import com.office.tracker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * End-of-day reliability sweep (missed-day catch-up).
 *
 * Fired at several points during the day to detect when the app failed to
 * log an arrival and/or departure, and to surface that to the user with an
 * actionable notification. Converts a silent miss into a caught one.
 *
 * Check types (EXTRA type):
 *   - "arrival"    : fired mid/late arrival window + shortly after; warns if
 *                    today has no arrival logged yet.
 *   - "departure"  : fired after the departure window; warns if today has an
 *                    arrival but no departure (user still "at office").
 *
 * No location is accessed here; it purely inspects the Room DB.
 */
class IntegrityCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE) ?: return
        Log.d(TAG, "Integrity check fired: type=$type")

        // Re-arm today's checks opportunistically (self-healing) — cheap.
        WindowScheduler.scheduleIntegrityChecks(context)

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dao = OfficeApp.instance.database.officeVisitDao()

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val todayVisit = try {
                dao.getVisitForDate(date)
            } catch (e: Exception) {
                Log.e(TAG, "DB read failed", e)
                null
            }

            when (type) {
                TYPE_ARRIVAL -> {
                    if (todayVisit == null || todayVisit.arrivalTime == null) {
                        notify(
                            context,
                            NOTIF_ID_ARRIVAL,
                            "No arrival detected",
                            "It's past the arrival window but nothing was logged today. " +
                                "Did you go to the office?",
                            "Mark arrival"
                        )
                    }
                }
                TYPE_DEPARTURE -> {
                    if (todayVisit != null && todayVisit.arrivalTime != null &&
                        todayVisit.departureTime == null && !todayVisit.isCurrentlyAtOffice
                    ) {
                        notify(
                            context,
                            NOTIF_ID_DEPARTURE,
                            "Departure not logged",
                            "You arrived at ${todayVisit.arrivalTime} but no departure " +
                                "was recorded. Did you leave early?",
                            "Log departure"
                        )
                    }
                }
            }
        }
    }

    private fun notify(
        context: Context,
        id: Int,
        title: String,
        body: String,
        actionLabel: String
    ) {
        val contentIntent = PendingIntent.getActivity(
            context, 200 + id,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_TAB, 1) // History tab
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, OfficeApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .addAction(
                Notification.Action.Builder(null, actionLabel, contentIntent).build()
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
        manager.notify(id, notification)
    }

    companion object {
        private const val TAG = "IntegrityCheck"
        const val EXTRA_TYPE = "check_type"
        const val TYPE_ARRIVAL = "arrival"
        const val TYPE_DEPARTURE = "departure"
        const val NOTIF_ID_ARRIVAL = 2001
        const val NOTIF_ID_DEPARTURE = 2002
    }
}
