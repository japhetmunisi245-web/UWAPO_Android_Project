package com.uwapo.app.sync

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uwapo.app.R
import com.uwapo.app.data.BackupPreferences
import com.uwapo.app.data.DatabaseBackupManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScheduledBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val backupPrefs = BackupPreferences(applicationContext)
        if (!backupPrefs.isScheduledBackupEnabled()) {
            return Result.success()
        }

        val treeUri = backupPrefs.getBackupFolderUri() ?: return Result.failure()

        return try {
            val documentDir = DocumentFile.fromTreeUri(applicationContext, treeUri)
            if (documentDir == null || !documentDir.canWrite()) {
                sendBackupNotification("Backup Failed", "Cannot write to selected backup directory.")
                return Result.failure()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "UWAPO_Backup_$timestamp.uwapo"

            val backupFile = documentDir.createFile("application/octet-stream", fileName)
                ?: return Result.failure()

            val outputStream = applicationContext.contentResolver.openOutputStream(backupFile.uri)
                ?: return Result.failure()

            val password = backupPrefs.getBackupPassword()
            val success = DatabaseBackupManager.exportDatabase(
                applicationContext,
                outputStream,
                password
            )

            if (success) {
                backupPrefs.setLastBackupTime(System.currentTimeMillis())
                sendBackupNotification("Backup Complete", "Database backed up to $fileName")
                Result.success()
            } else {
                sendBackupNotification("Backup Failed", "Failed to export database.")
                Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sendBackupNotification("Backup Error", e.localizedMessage ?: "Unknown error")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun sendBackupNotification(title: String, message: String) {
        try {
            val notification = NotificationCompat.Builder(applicationContext, "uwapo_backups")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()

            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1002, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
