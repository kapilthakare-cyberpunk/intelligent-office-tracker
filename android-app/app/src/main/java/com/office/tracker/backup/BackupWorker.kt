package com.office.tracker.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log
import com.office.tracker.util.Prefs

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!Prefs.getBackupEnabled(applicationContext)) {
            return Result.success()
        }
        return try {
            val message = BackupManager.backupNow(applicationContext)
            Log.d(TAG, "backup done: $message")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "backup failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "BackupWorker"
    }
}
