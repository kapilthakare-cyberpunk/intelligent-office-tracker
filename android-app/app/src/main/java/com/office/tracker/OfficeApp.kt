package com.office.tracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.office.tracker.db.AppDatabase

class OfficeApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Office Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Tracks your office arrival and departure times"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "office_tracking"

        lateinit var instance: OfficeApp
            private set
    }
}
