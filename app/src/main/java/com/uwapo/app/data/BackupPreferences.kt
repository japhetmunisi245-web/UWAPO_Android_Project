package com.uwapo.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.uwapo.app.sync.ScheduledBackupWorker
import java.util.concurrent.TimeUnit

class BackupPreferences(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "uwapo_backup_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.getSharedPreferences("uwapo_backup_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun isScheduledBackupEnabled(): Boolean = prefs.getBoolean(KEY_SCHEDULED_ENABLED, false)

    fun setScheduledBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCHEDULED_ENABLED, enabled).apply()
        if (enabled) {
            scheduleWorker()
        } else {
            cancelWorker()
        }
    }

    fun getBackupFolderUri(): Uri? {
        val uriString = prefs.getString(KEY_FOLDER_URI, null) ?: return null
        return Uri.parse(uriString)
    }

    fun setBackupFolderUri(uri: Uri?) {
        if (uri == null) {
            prefs.edit().remove(KEY_FOLDER_URI).apply()
        } else {
            prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
        }
    }

    fun getBackupPassword(): String? = prefs.getString(KEY_BACKUP_PASSWORD, null)

    fun setBackupPassword(password: String?) {
        if (password.isNullOrBlank()) {
            prefs.edit().remove(KEY_BACKUP_PASSWORD).apply()
        } else {
            prefs.edit().putString(KEY_BACKUP_PASSWORD, password).apply()
        }
    }

    fun getBackupFrequency(): String = prefs.getString(KEY_BACKUP_FREQUENCY, "DAILY") ?: "DAILY"

    fun setBackupFrequency(frequency: String) {
        prefs.edit().putString(KEY_BACKUP_FREQUENCY, frequency).apply()
        if (isScheduledBackupEnabled()) {
            scheduleWorker()
        }
    }

    fun getLastBackupTime(): Long = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)

    fun setLastBackupTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP_TIME, time).apply()
    }

    fun scheduleWorker() {
        if (!isScheduledBackupEnabled() || getBackupFolderUri() == null) {
            cancelWorker()
            return
        }

        val frequencyDays = if (getBackupFrequency() == "WEEKLY") 7L else 1L
        val workRequest = PeriodicWorkRequestBuilder<ScheduledBackupWorker>(
            frequencyDays, TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancelWorker() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "UWAPO_SCHEDULED_BACKUP"
        private const val KEY_SCHEDULED_ENABLED = "scheduled_enabled"
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_BACKUP_PASSWORD = "backup_password"
        private const val KEY_BACKUP_FREQUENCY = "backup_frequency"
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
    }
}
