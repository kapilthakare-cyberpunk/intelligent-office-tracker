package com.office.tracker.service

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.office.tracker.OfficeApp
import com.office.tracker.db.VisitRepository
import com.office.tracker.ui.MainActivity
import com.office.tracker.util.format12h
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Missed-day catch-up sweep. Inspects the DB only; surfaces missing arrival /
 * departure with an actionable notification, and re-arms the schedule (self-heal).
 */
class IntegrityCheckReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE) ?: return
        Log.d(TAG, "Integrity check fired: type=$type")

        // Honest self-heal: cancel + reschedule windows/integrity + persist plan.
        WindowScheduler.ensureLatest(context)

        val repository = VisitRepository(OfficeApp.instance.database.officeVisitDao())
        val date = LocalDate.now().toString()
        scope.launch {
            val todayVisit = try {
                repository.visitForDateOnce(date)
            } catch (e: Exception) {
                Log.e(TAG, "DB read failed", e)
                null
            }
            when (type) {
                TYPE_ARRIVAL -> {
                    if (todayVisit == null || todayVisit.arrivalTime == null) {
                        notify(
                            context, NOTIF_ID_ARRIVAL, "No arrival detected",
                            "It's past the arrival window but nothing was logged today. Did you go to the office?",
                            "Mark arrival"
                        )
                    }
                }
                TYPE_DEPARTURE -> {
                    if (todayVisit != null && todayVisit.arrivalTime != null &&
                        todayVisit.departureTime == null && !todayVisit.isCurrentlyAtOffice
                    ) {
                        notify(
                            context, NOTIF_ID_DEPARTURE, "Departure not logged",
                            "You arrived at ${format12h(todayVisit.arrivalTime) ?: todayVisit.arrivalTime} but no departure was recorded.",
                            "Log departure"
                        )
                    }
                }
            }
        }
    }

    private fun notify(context: Context, id: Int, title: String, body: String, actionLabel: String) {
        val contentIntent = PendingIntent.getActivity(
            context, 200 + id,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_TAB, 1)
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
            .addAction(Notification.Action.Builder(null, actionLabel, contentIntent).build())
            .build()
        context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            .notify(id, notification)
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
