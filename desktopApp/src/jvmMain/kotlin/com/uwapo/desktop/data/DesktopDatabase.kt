package com.uwapo.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Thrown when a member number (or id) is already registered, instead of silently overwriting that member. */
class DuplicateMemberException(message: String) : Exception(message)

data class MemberEntity(
    val id: String,
    val memberNo: String,
    val fullName: String,
    val phone: String = "",
    val joinedAt: Long = System.currentTimeMillis(),
    val status: String = "active",
    val syncStatus: String = "local",
    val notes: String = "",
    val batchYear: Int = 2025
)

data class PaymentEntity(
    val id: String,
    val memberId: String,
    val amountTzs: Long,
    val periodYear: Int,
    val periodMonth: Int,
    val paidAt: Long = System.currentTimeMillis(),
    val method: String = "cash",
    val receiptNo: String,
    val requestId: String = UUID.randomUUID().toString(),
    val syncStatus: String = "local"
)

data class ExpenseEntity(
    val id: String,
    val category: String,
    val description: String,
    val amountTzs: Long,
    val expenseDate: Long = System.currentTimeMillis(),
    val accountName: String = "Cash",
    val status: String = "pending_approval",
    val syncStatus: String = "local",
    val receiptImagePath: String = ""
)

class DesktopDatabase {
    private val dbFile: File
    private val backupDir: File
    private var lastBackupAt = 0L

    /** Folder where automatic safety copies are written before imports and deletes. */
    val backupFolderPath: String get() = backupDir.absolutePath

    private val _members = MutableStateFlow<List<MemberEntity>>(emptyList())
    val members: StateFlow<List<MemberEntity>> = _members.asStateFlow()

    private val _payments = MutableStateFlow<List<PaymentEntity>>(emptyList())
    val payments: StateFlow<List<PaymentEntity>> = _payments.asStateFlow()

    private val _expenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    val expenses: StateFlow<List<ExpenseEntity>> = _expenses.asStateFlow()

    private val _batches = MutableStateFlow<List<Int>>(emptyList())
    val batches: StateFlow<List<Int>> = _batches.asStateFlow()

    init {
        val userHome = System.getProperty("user.home") ?: "."
        val appDir = File(userHome, ".uwapo")
        if (!appDir.exists()) appDir.mkdirs()
        dbFile = File(appDir, "uwapo_desktop.db")
        backupDir = File(appDir, "backups")

        try {
            Class.forName("org.sqlite.JDBC")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        initTables()
        refreshAll()
    }

    private fun getConnection(): Connection {
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.absolutePath)
    }

    private fun initTables() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS members (
                        id TEXT PRIMARY KEY,
                        memberNo TEXT UNIQUE,
                        fullName TEXT NOT NULL,
                        phone TEXT DEFAULT '',
                        joinedAt INTEGER,
                        status TEXT DEFAULT 'active',
                        syncStatus TEXT DEFAULT 'local',
                        notes TEXT DEFAULT '',
                        batchYear INTEGER DEFAULT 2025
                    );
                """.trimIndent())

                // In case table existed without batchYear
                try {
                    stmt.execute("ALTER TABLE members ADD COLUMN batchYear INTEGER DEFAULT 2025;")
                } catch (ignored: Exception) {}

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS payments (
                        id TEXT PRIMARY KEY,
                        memberId TEXT NOT NULL,
                        amountTzs INTEGER NOT NULL,
                        periodYear INTEGER NOT NULL,
                        periodMonth INTEGER NOT NULL,
                        paidAt INTEGER,
                        method TEXT DEFAULT 'cash',
                        receiptNo TEXT NOT NULL,
                        requestId TEXT UNIQUE,
                        syncStatus TEXT DEFAULT 'local'
                    );
                """.trimIndent())

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS expenses (
                        id TEXT PRIMARY KEY,
                        category TEXT NOT NULL,
                        description TEXT DEFAULT '',
                        amountTzs INTEGER NOT NULL,
                        expenseDate INTEGER,
                        accountName TEXT DEFAULT 'Cash',
                        status TEXT DEFAULT 'pending_approval',
                        syncStatus TEXT DEFAULT 'local',
                        receiptImagePath TEXT DEFAULT ''
                    );
                """.trimIndent())

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS batches (
                        year INTEGER PRIMARY KEY
                    );
                """.trimIndent())

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_payments_member ON payments(memberId)")
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_payments_period ON payments(periodYear, periodMonth)")
            }
        }
    }

    /**
     * Writes a consistent snapshot of the database to the backups folder (VACUUM INTO).
     * Destructive actions pass force = false so a burst of deletes makes one backup, not dozens.
     * Returns null if skipped or if the copy could not be made.
     */
    fun backup(reason: String, force: Boolean = true): File? {
        val now = System.currentTimeMillis()
        if (!force && now - lastBackupAt < 120_000) return null
        return try {
            backupDir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(now))
            val safeReason = reason.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9-]"), "-")
            val target = File(backupDir, "uwapo_${stamp}_$safeReason.db")
            getConnection().use { conn ->
                conn.createStatement().use { it.execute("VACUUM INTO '" + target.absolutePath.replace("'", "''") + "'") }
            }
            lastBackupAt = now
            pruneBackups()
            target
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun pruneBackups(keep: Int = 40) {
        backupDir.listFiles { f -> f.name.startsWith("uwapo_") && f.name.endsWith(".db") }
            ?.sortedByDescending { it.name }
            ?.drop(keep)
            ?.forEach { it.delete() }
    }

    fun existingPaymentIds(): Set<String> {
        val ids = HashSet<String>()
        getConnection().use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT id FROM payments").use { rs -> while (rs.next()) ids.add(rs.getString(1)) }
            }
        }
        return ids
    }

    /**
     * Saves an import plan in ONE transaction on ONE connection and refreshes the screen state once.
     */
    fun importBatch(newMembers: List<MemberEntity>, payments: List<PaymentEntity>): Pair<Int, Int> {
        if (newMembers.isEmpty() && payments.isEmpty()) return 0 to 0
        backup("before-import")
        var membersAdded = 0
        var paymentsAdded = 0
        getConnection().use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(
                    "INSERT OR IGNORE INTO members (id, memberNo, fullName, phone, joinedAt, status, syncStatus, notes, batchYear) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                ).use { ps ->
                    for (m in newMembers) {
                        ps.setString(1, m.id); ps.setString(2, m.memberNo); ps.setString(3, m.fullName)
                        ps.setString(4, m.phone); ps.setLong(5, m.joinedAt); ps.setString(6, m.status)
                        ps.setString(7, m.syncStatus); ps.setString(8, m.notes); ps.setInt(9, m.batchYear)
                        membersAdded += ps.executeUpdate()
                    }
                }

                val validMemberIds = HashSet<String>()
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT id FROM members").use { rs -> while (rs.next()) validMemberIds.add(rs.getString(1)) }
                }
                if (payments.any { it.memberId !in validMemberIds }) {
                    throw IllegalStateException("Import aborted: a payment refers to a member that could not be created (member number clash). Nothing was saved.")
                }

                conn.prepareStatement(
                    "INSERT OR IGNORE INTO payments (id, memberId, amountTzs, periodYear, periodMonth, paidAt, method, receiptNo, requestId, syncStatus) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                ).use { ps ->
                    for (p in payments) {
                        ps.setString(1, p.id); ps.setString(2, p.memberId); ps.setLong(3, p.amountTzs)
                        ps.setInt(4, p.periodYear); ps.setInt(5, p.periodMonth); ps.setLong(6, p.paidAt)
                        ps.setString(7, p.method); ps.setString(8, p.receiptNo); ps.setString(9, p.requestId)
                        ps.setString(10, p.syncStatus)
                        paymentsAdded += ps.executeUpdate()
                    }
                }

                // Ensure batch years exist in batches table
                val batchYearsToInsert = (newMembers.map { it.batchYear } + payments.map { it.periodYear }).distinct()
                conn.prepareStatement("INSERT OR IGNORE INTO batches (year) VALUES (?)").use { ps ->
                    for (yr in batchYearsToInsert) {
                        ps.setInt(1, yr)
                        ps.executeUpdate()
                    }
                }

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
        refreshAll()
        return membersAdded to paymentsAdded
    }

    fun refreshAll() {
        refreshMembers()
        refreshPayments()
        refreshExpenses()
        refreshBatches()
    }

    fun refreshMembers() {
        val list = mutableListOf<MemberEntity>()
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT * FROM members ORDER BY fullName COLLATE NOCASE")
                while (rs.next()) {
                    val batchYrRaw = try { rs.getInt("batchYear") } catch (e: Exception) { 2025 }
                    val batchYr = if (batchYrRaw == 0) 2025 else batchYrRaw
                    list.add(
                        MemberEntity(
                            id = rs.getString("id"),
                            memberNo = rs.getString("memberNo"),
                            fullName = rs.getString("fullName"),
                            phone = rs.getString("phone") ?: "",
                            joinedAt = rs.getLong("joinedAt"),
                            status = rs.getString("status") ?: "active",
                            syncStatus = rs.getString("syncStatus") ?: "local",
                            notes = rs.getString("notes") ?: "",
                            batchYear = batchYr
                        )
                    )
                }
            }
        }
        _members.value = list
    }

    fun refreshPayments() {
        val list = mutableListOf<PaymentEntity>()
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT * FROM payments ORDER BY paidAt DESC")
                while (rs.next()) {
                    list.add(
                        PaymentEntity(
                            id = rs.getString("id"),
                            memberId = rs.getString("memberId"),
                            amountTzs = rs.getLong("amountTzs"),
                            periodYear = rs.getInt("periodYear"),
                            periodMonth = rs.getInt("periodMonth"),
                            paidAt = rs.getLong("paidAt"),
                            method = rs.getString("method") ?: "cash",
                            receiptNo = rs.getString("receiptNo"),
                            requestId = rs.getString("requestId") ?: UUID.randomUUID().toString(),
                            syncStatus = rs.getString("syncStatus") ?: "local"
                        )
                    )
                }
            }
        }
        _payments.value = list
    }

    fun refreshExpenses() {
        val list = mutableListOf<ExpenseEntity>()
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT * FROM expenses ORDER BY expenseDate DESC")
                while (rs.next()) {
                    list.add(
                        ExpenseEntity(
                            id = rs.getString("id"),
                            category = rs.getString("category"),
                            description = rs.getString("description") ?: "",
                            amountTzs = rs.getLong("amountTzs"),
                            expenseDate = rs.getLong("expenseDate"),
                            accountName = rs.getString("accountName") ?: "Cash",
                            status = rs.getString("status") ?: "pending_approval",
                            syncStatus = rs.getString("syncStatus") ?: "local",
                            receiptImagePath = rs.getString("receiptImagePath") ?: ""
                        )
                    )
                }
            }
        }
        _expenses.value = list
    }

    fun refreshBatches() {
        val list = mutableSetOf<Int>()
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("""
                    SELECT year FROM batches
                    UNION
                    SELECT DISTINCT periodYear FROM payments
                    UNION
                    SELECT DISTINCT batchYear FROM members
                    ORDER BY year DESC
                """.trimIndent()).use { rs ->
                    while (rs.next()) {
                        val y = rs.getInt(1)
                        if (y > 0) list.add(y)
                    }
                }
            }
        }
        if (list.isEmpty()) list.add(2025)
        _batches.value = list.sortedDescending()
    }

    fun insertBatch(year: Int) {
        getConnection().use { conn ->
            conn.prepareStatement("INSERT OR IGNORE INTO batches (year) VALUES (?)").use { pstmt ->
                pstmt.setInt(1, year)
                pstmt.executeUpdate()
            }
        }
        refreshBatches()
    }

    fun deleteBatch(year: Int) {
        backup("before-delete-batch-$year", force = false)
        getConnection().use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(
                    "DELETE FROM payments WHERE periodYear = ? OR memberId IN (SELECT id FROM members WHERE batchYear = ?)"
                ).use { ps ->
                    ps.setInt(1, year)
                    ps.setInt(2, year)
                    ps.executeUpdate()
                }
                conn.prepareStatement("DELETE FROM members WHERE batchYear = ?").use { ps ->
                    ps.setInt(1, year)
                    ps.executeUpdate()
                }
                conn.prepareStatement("DELETE FROM batches WHERE year = ?").use { ps ->
                    ps.setInt(1, year)
                    ps.executeUpdate()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
        refreshAll()
    }

    fun insertMember(m: MemberEntity) {
        try {
            getConnection().use { conn ->
                val sql = "INSERT INTO members (id, memberNo, fullName, phone, joinedAt, status, syncStatus, notes, batchYear) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, m.id)
                    pstmt.setString(2, m.memberNo)
                    pstmt.setString(3, m.fullName)
                    pstmt.setString(4, m.phone)
                    pstmt.setLong(5, m.joinedAt)
                    pstmt.setString(6, m.status)
                    pstmt.setString(7, m.syncStatus)
                    pstmt.setString(8, m.notes)
                    pstmt.setInt(9, m.batchYear)
                    pstmt.executeUpdate()
                }
                conn.prepareStatement("INSERT OR IGNORE INTO batches (year) VALUES (?)").use { ps ->
                    ps.setInt(1, m.batchYear)
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            if (e.message?.contains("UNIQUE", ignoreCase = true) == true || e.message?.contains("constraint", ignoreCase = true) == true) {
                throw DuplicateMemberException("A member with number \"${m.memberNo}\" already exists. Choose a different number.")
            }
            throw e
        }
        refreshMembers()
        refreshBatches()
    }

    fun insertPayment(p: PaymentEntity) {
        getConnection().use { conn ->
            conn.prepareStatement("SELECT 1 FROM members WHERE id = ?").use { check ->
                check.setString(1, p.memberId)
                check.executeQuery().use { rs ->
                    if (!rs.next()) throw IllegalStateException("This member no longer exists, so the payment was not saved.")
                }
            }
            val sql = "INSERT INTO payments (id, memberId, amountTzs, periodYear, periodMonth, paidAt, method, receiptNo, requestId, syncStatus) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, p.id)
                pstmt.setString(2, p.memberId)
                pstmt.setLong(3, p.amountTzs)
                pstmt.setInt(4, p.periodYear)
                pstmt.setInt(5, p.periodMonth)
                pstmt.setLong(6, p.paidAt)
                pstmt.setString(7, p.method)
                pstmt.setString(8, p.receiptNo)
                pstmt.setString(9, p.requestId)
                pstmt.setString(10, p.syncStatus)
                pstmt.executeUpdate()
            }
            conn.prepareStatement("INSERT OR IGNORE INTO batches (year) VALUES (?)").use { ps ->
                ps.setInt(1, p.periodYear)
                ps.executeUpdate()
            }
        }
        refreshPayments()
        refreshBatches()
    }

    fun reversePayment(paymentId: String, reason: String) {
        backup("before-reverse-payment", force = false)
        val statusStr = if (reason.isNotBlank()) "reversed: $reason" else "reversed"
        getConnection().use { conn ->
            conn.prepareStatement("UPDATE payments SET syncStatus = ? WHERE id = ?").use { pstmt ->
                pstmt.setString(1, statusStr)
                pstmt.setString(2, paymentId)
                pstmt.executeUpdate()
            }
        }
        refreshPayments()
    }

    fun insertExpense(e: ExpenseEntity) {
        getConnection().use { conn ->
            val sql = "INSERT OR REPLACE INTO expenses (id, category, description, amountTzs, expenseDate, accountName, status, syncStatus, receiptImagePath) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, e.id)
                pstmt.setString(2, e.category)
                pstmt.setString(3, e.description)
                pstmt.setLong(4, e.amountTzs)
                pstmt.setLong(5, e.expenseDate)
                pstmt.setString(6, e.accountName)
                pstmt.setString(7, e.status)
                pstmt.setString(8, e.syncStatus)
                pstmt.setString(9, e.receiptImagePath)
                pstmt.executeUpdate()
            }
        }
        refreshExpenses()
    }

    fun deleteMember(memberId: String) {
        backup("before-delete-member", force = false)
        getConnection().use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement("DELETE FROM payments WHERE memberId = ?").use { pstmt ->
                    pstmt.setString(1, memberId)
                    pstmt.executeUpdate()
                }
                conn.prepareStatement("DELETE FROM members WHERE id = ?").use { pstmt ->
                    pstmt.setString(1, memberId)
                    pstmt.executeUpdate()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
        refreshMembers()
        refreshPayments()
    }

    fun deletePayment(paymentId: String) {
        backup("before-delete-payment", force = false)
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM payments WHERE id = ?").use { pstmt ->
                pstmt.setString(1, paymentId)
                pstmt.executeUpdate()
            }
        }
        refreshPayments()
    }

    fun deleteExpense(expenseId: String) {
        backup("before-delete-expense", force = false)
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM expenses WHERE id = ?").use { pstmt ->
                pstmt.setString(1, expenseId)
                pstmt.executeUpdate()
            }
        }
        refreshExpenses()
    }

    fun updateExpenseStatus(expenseId: String, status: String) {
        getConnection().use { conn ->
            conn.prepareStatement("UPDATE expenses SET status = ? WHERE id = ?").use { pstmt ->
                pstmt.setString(1, status)
                pstmt.setString(2, expenseId)
                pstmt.executeUpdate()
            }
        }
        refreshExpenses()
    }

    fun updateMemberProfile(id: String, phone: String, status: String, notes: String) {
        getConnection().use { conn ->
            conn.prepareStatement("UPDATE members SET phone = ?, status = ?, notes = ? WHERE id = ?").use { pstmt ->
                pstmt.setString(1, phone)
                pstmt.setString(2, status)
                pstmt.setString(3, notes)
                pstmt.setString(4, id)
                pstmt.executeUpdate()
            }
        }
        refreshMembers()
    }

    fun getBackupFiles(): List<File> {
        return backupDir.listFiles { f -> f.name.startsWith("uwapo_") && f.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun restoreBackup(backupFile: File): Boolean {
        if (!backupFile.exists()) return false
        backup("before-restore")
        return try {
            backupFile.copyTo(dbFile, overwrite = true)
            refreshAll()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun vacuumDatabase(): Boolean {
        return try {
            getConnection().use { conn ->
                conn.createStatement().use { st -> st.execute("VACUUM;") }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun checkIntegrity(): String {
        return try {
            getConnection().use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery("PRAGMA integrity_check;").use { rs ->
                        if (rs.next()) rs.getString(1) else "Unknown"
                    }
                }
            }
        } catch (e: Exception) {
            e.message ?: "Check failed"
        }
    }

    fun getDbFileSize(): Long = dbFile.length()
}
