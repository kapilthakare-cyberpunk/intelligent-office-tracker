package com.office.tracker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.office.tracker.service.OfficeTrackingService
import com.office.tracker.service.WindowScheduler

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            startTracking()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = dynamicColorScheme()
            ) {
                OfficeTrackerApp(
                    onStartTracking = { requestPermissionsAndStart() },
                    onStopTracking = { stopTracking() },
                    initialTab = (intent?.getIntExtra(EXTRA_OPEN_TAB, -1) ?: -1).let {
                        if (it in 0..2) it else null
                    }
                )
            }
        }

        // Schedule windows on launch
        WindowScheduler.scheduleToday(this)
        WindowScheduler.scheduleTomorrow(this)
        WindowScheduler.scheduleIntegrityChecks(this)
    }

    private fun requestPermissionsAndStart() {
        val needed = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (needed.isEmpty()) {
            startTracking()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startTracking() {
        // Request background location separately (Android 11+)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Will need to guide user to settings for background location
            // For now, foreground service + fine location is sufficient for windowed tracking
        }

        val intent = Intent(this, OfficeTrackingService::class.java).apply {
            action = WindowScheduler.ACTION_WINDOW_START
            putExtra(WindowScheduler.EXTRA_WINDOW_TYPE, "arrival")
        }
        startForegroundService(intent)
    }

    private fun stopTracking() {
        val intent = Intent(this, OfficeTrackingService::class.java).apply {
            action = "com.office.tracker.STOP"
        }
        startForegroundService(intent)
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
    }
}

@Composable
fun dynamicColorScheme(): ColorScheme {
    return MaterialTheme.colorScheme
}
