package com.office.tracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WindowAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val windowType = intent.getStringExtra(WindowScheduler.EXTRA_WINDOW_TYPE) ?: return
        val action = intent.action

        Log.d("WindowAlarmReceiver", "Alarm fired: action=$action, window=$windowType")

        val serviceIntent = Intent(context, OfficeTrackingService::class.java).apply {
            this.action = action
            putExtra(WindowScheduler.EXTRA_WINDOW_TYPE, windowType)
        }

        if (action == WindowScheduler.ACTION_WINDOW_START) {
            // Start the foreground service
            context.startForegroundService(serviceIntent)
        } else if (action == WindowScheduler.ACTION_WINDOW_END) {
            // Signal service to stop location checks for this window
            context.startForegroundService(serviceIntent)
        }
    }
}
