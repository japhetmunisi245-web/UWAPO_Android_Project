package com.uwapo.app

import android.app.Application
import androidx.room.Room
import com.uwapo.app.data.UWAPODatabase

class UWAPOApplication : Application() {
    lateinit var database: UWAPODatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(this, UWAPODatabase::class.java, "uwapo.db")
            .addMigrations(UWAPODatabase.MIGRATION_1_2, UWAPODatabase.MIGRATION_2_3)
            .build()
    }
}
