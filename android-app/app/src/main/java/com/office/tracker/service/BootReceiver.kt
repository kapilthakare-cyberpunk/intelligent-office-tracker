package com.office.tracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-schedules all windows after device reboot.
 * Without this, AlarmManager alarms are lost on reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WindowScheduler.scheduleToday(context)
            WindowScheduler.scheduleTomorrow(context)
        }
    }
}
