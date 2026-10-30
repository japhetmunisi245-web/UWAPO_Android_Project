package com.uwapo.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import com.uwapo.app.data.UWAPODatabase
import com.uwapo.app.security.SecurityManager
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

class UWAPOApplication : Application() {
    lateinit var database: UWAPODatabase
        private set

    lateinit var securityManager: SecurityManager
        private set

    override fun onCreate() {
        super.onCreate()
        securityManager = SecurityManager(this)
        initDatabase()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "uwapo_due_reminders",
                "UWAPO Contribution Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for monthly contribution due dates"
            }
            val backupChannel = NotificationChannel(
                "uwapo_backups",
                "UWAPO Backups",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for scheduled database backups"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
            notificationManager?.createNotificationChannel(backupChannel)
        }
    }

    fun initDatabase() {
        val builder = Room.databaseBuilder(this, UWAPODatabase::class.java, "uwapo.db")
            .addMigrations(
                UWAPODatabase.MIGRATION_1_2,
                UWAPODatabase.MIGRATION_2_3,
                UWAPODatabase.MIGRATION_3_4,
                UWAPODatabase.MIGRATION_4_5
            )
            .addCallback(UWAPODatabase.SEED_CALLBACK)
            .fallbackToDestructiveMigrationOnDowngrade()

        val dbPassphrase = securityManager.getDatabasePassphrase()
        if (!dbPassphrase.isNullOrBlank()) {
            SQLiteDatabase.loadLibs(this)
            val factory = SupportFactory(SQLiteDatabase.getBytes(dbPassphrase.toCharArray()))
            builder.openHelperFactory(factory)
        }

        database = builder.build()
    }

    fun checkpointDatabase() {
        try {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun closeDatabase() {
        try {
            if (::database.isInitialized && database.isOpen) {
                database.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun reopenDatabase() {
        closeDatabase()
        initDatabase()
    }
}
