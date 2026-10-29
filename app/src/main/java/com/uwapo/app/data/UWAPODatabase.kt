package com.uwapo.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BatchEntity::class, MemberEntity::class, PaymentEntity::class, ExpenseEntity::class], version = 5, exportSchema = true)
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

        /** Batches that exist on a fresh install (the years the app always offered before). */
        val DEFAULT_BATCH_YEARS = 2023..2027

        /**
         * Seeds the default batches on a brand-new database. Migrated databases are seeded
         * by [MIGRATION_4_5]; after that batches are fully user-managed (add / delete).
         */
        val SEED_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                DEFAULT_BATCH_YEARS.forEach { year ->
                    db.execSQL("INSERT OR IGNORE INTO batches (year, createdAt) VALUES ($year, $now)")
                }
            }
        }

        /**
         * Version 5: every batch owns its own members.
         *
         * Before this, one global member list was shared by all years, and a member simply
         * "appeared" in every batch. Now each member row has a batchYear. A member who has
         * payments in several years is split into one independent copy per year, and each copy
         * takes only the payments of its own year. Members with no payments go to the year
         * they joined. Nothing is deleted.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS batches (year INTEGER NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL)")
                db.execSQL("ALTER TABLE members ADD COLUMN batchYear INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP INDEX IF EXISTS index_members_memberNo")

                val zone = java.util.TimeZone.getTimeZone("Africa/Dar_es_Salaam")
                val usedYears = sortedSetOf<Int>()

                data class OldMember(
                    val id: String, val memberNo: String, val fullName: String, val phone: String,
                    val joinedAt: Long, val status: String, val syncStatus: String, val notes: String
                )

                val members = mutableListOf<OldMember>()
                db.query("SELECT id, memberNo, fullName, phone, joinedAt, status, syncStatus, notes FROM members").use { c ->
                    while (c.moveToNext()) {
                        members += OldMember(
                            c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                            c.getLong(4), c.getString(5), c.getString(6), c.getString(7)
                        )
                    }
                }

                for (m in members) {
                    val paymentYears = mutableListOf<Int>()
                    db.query("SELECT DISTINCT periodYear FROM payments WHERE memberId = ? ORDER BY periodYear", arrayOf<Any>(m.id)).use { c ->
                        while (c.moveToNext()) paymentYears += c.getInt(0)
                    }
                    val years = if (paymentYears.isNotEmpty()) paymentYears else {
                        val cal = java.util.Calendar.getInstance(zone)
                        cal.timeInMillis = m.joinedAt
                        listOf(cal.get(java.util.Calendar.YEAR))
                    }
                    years.forEachIndexed { index, year ->
                        usedYears += year
                        if (index == 0) {
                            db.execSQL("UPDATE members SET batchYear = ? WHERE id = ?", arrayOf<Any>(year, m.id))
                        } else {
                            val copyId = java.util.UUID.nameUUIDFromBytes("UWAPO-MEMBER:$year:${m.memberNo}:${m.id}".toByteArray(Charsets.UTF_8)).toString()
                            db.execSQL(
                                "INSERT INTO members (id, memberNo, fullName, phone, joinedAt, status, syncStatus, notes, batchYear) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                arrayOf<Any>(copyId, m.memberNo, m.fullName, m.phone, m.joinedAt, m.status, m.syncStatus, m.notes, year)
                            )
                            db.execSQL(
                                "UPDATE payments SET memberId = ? WHERE memberId = ? AND periodYear = ?",
                                arrayOf<Any>(copyId, m.id, year)
                            )
                        }
                    }
                }

                val now = System.currentTimeMillis()
                (usedYears + DEFAULT_BATCH_YEARS.toList()).forEach { year ->
                    db.execSQL("INSERT OR IGNORE INTO batches (year, createdAt) VALUES ($year, $now)")
                }

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_members_batchYear_memberNo ON members(batchYear, memberNo)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_members_batchYear ON members(batchYear)")
            }
        }
    }
}
