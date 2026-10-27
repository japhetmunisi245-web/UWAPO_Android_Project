package com.uwapo.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import com.uwapo.app.data.UWAPODatabase

class UWAPOApplication : Application() {
    lateinit var database: UWAPODatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(this, UWAPODatabase::class.java, "uwapo.db")
            .addMigrations(
                UWAPODatabase.MIGRATION_1_2,
                UWAPODatabase.MIGRATION_2_3,
                UWAPODatabase.MIGRATION_3_4
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "uwapo_due_reminders",
                "UWAPO Contribution Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for monthly contribution due dates"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
