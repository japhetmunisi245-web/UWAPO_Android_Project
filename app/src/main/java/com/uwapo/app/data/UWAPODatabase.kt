package com.uwapo.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MemberEntity::class, PaymentEntity::class, ExpenseEntity::class], version = 4, exportSchema = false)
abstract class UWAPODatabase : RoomDatabase() {
    abstract fun uwapoDao(): UWAPODao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE members ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'pending'")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS expenses (id TEXT NOT NULL PRIMARY KEY, category TEXT NOT NULL, description TEXT NOT NULL, amountTzs INTEGER NOT NULL, expenseDate INTEGER NOT NULL, accountName TEXT NOT NULL, status TEXT NOT NULL, syncStatus TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_expenseDate ON expenses(expenseDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_syncStatus ON expenses(syncStatus)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE members ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN receiptImagePath TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
