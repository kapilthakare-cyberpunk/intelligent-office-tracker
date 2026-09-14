package com.office.tracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class WindowAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val windowType = WindowType.fromWire(intent.getStringExtra(WindowType.EXTRA_WINDOW_TYPE))
        val action = intent.action ?: return

        // Integrity-check alarms are delivered here too; route them on.
        if (action == WindowScheduler.ACTION_INTEGRITY_ARRIVAL ||
            action == WindowScheduler.ACTION_INTEGRITY_DEPARTURE
        ) {
            Log.d(TAG, "Integrity check alarm: $action")
            if (windowType == null) return
            val integrity = Intent(context, IntegrityCheckReceiver::class.java).apply {
                this.action = action
                putExtra(IntegrityCheckReceiver.EXTRA_TYPE, windowType.wire)
            }
            context.sendBroadcast(integrity)
            return
        }

        if (windowType == null) return
        val serviceIntent = Intent(context, OfficeTrackingService::class.java).apply {
            this.action = action
            putExtra(WindowType.EXTRA_WINDOW_TYPE, windowType.wire)
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start service from alarm: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "WindowAlarmReceiver"
    }
}
