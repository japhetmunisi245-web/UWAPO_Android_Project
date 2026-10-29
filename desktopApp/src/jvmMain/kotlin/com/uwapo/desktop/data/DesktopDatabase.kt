package com.uwapo.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

data class MemberEntity(
    val id: String,
    val memberNo: String,
    val fullName: String,
    val phone: String = "",
    val joinedAt: Long = System.currentTimeMillis(),
    val status: String = "active",
    val syncStatus: String = "local",
    val notes: String = ""
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

    private val _members = MutableStateFlow<List<MemberEntity>>(emptyList())
    val members: StateFlow<List<MemberEntity>> = _members.asStateFlow()

    private val _payments = MutableStateFlow<List<PaymentEntity>>(emptyList())
    val payments: StateFlow<List<PaymentEntity>> = _payments.asStateFlow()

    private val _expenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    val expenses: StateFlow<List<ExpenseEntity>> = _expenses.asStateFlow()

    init {
        val userHome = System.getProperty("user.home") ?: "."
        val appDir = File(userHome, ".uwapo")
        if (!appDir.exists()) appDir.mkdirs()
        dbFile = File(appDir, "uwapo_desktop.db")

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
                        notes TEXT DEFAULT ''
                    );
                """.trimIndent())

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
            }
        }
    }

    fun refreshAll() {
        refreshMembers()
        refreshPayments()
        refreshExpenses()
    }

    fun refreshMembers() {
        val list = mutableListOf<MemberEntity>()
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT * FROM members ORDER BY fullName COLLATE NOCASE")
                while (rs.next()) {
                    list.add(
                        MemberEntity(
                            id = rs.getString("id"),
                            memberNo = rs.getString("memberNo"),
                            fullName = rs.getString("fullName"),
                            phone = rs.getString("phone") ?: "",
                            joinedAt = rs.getLong("joinedAt"),
                            status = rs.getString("status") ?: "active",
                            syncStatus = rs.getString("syncStatus") ?: "local",
                            notes = rs.getString("notes") ?: ""
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

    fun insertMember(m: MemberEntity) {
        getConnection().use { conn ->
            val sql = "INSERT OR REPLACE INTO members (id, memberNo, fullName, phone, joinedAt, status, syncStatus, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, m.id)
                pstmt.setString(2, m.memberNo)
                pstmt.setString(3, m.fullName)
                pstmt.setString(4, m.phone)
                pstmt.setLong(5, m.joinedAt)
                pstmt.setString(6, m.status)
                pstmt.setString(7, m.syncStatus)
                pstmt.setString(8, m.notes)
                pstmt.executeUpdate()
            }
        }
        refreshMembers()
    }

    fun insertPayment(p: PaymentEntity) {
        getConnection().use { conn ->
            val sql = "INSERT OR REPLACE INTO payments (id, memberId, amountTzs, periodYear, periodMonth, paidAt, method, receiptNo, requestId, syncStatus) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
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
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM payments WHERE memberId = ?").use { pstmt ->
                pstmt.setString(1, memberId)
                pstmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM members WHERE id = ?").use { pstmt ->
                pstmt.setString(1, memberId)
                pstmt.executeUpdate()
            }
        }
        refreshMembers()
        refreshPayments()
    }

    fun deletePayment(paymentId: String) {
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM payments WHERE id = ?").use { pstmt ->
                pstmt.setString(1, paymentId)
                pstmt.executeUpdate()
            }
        }
        refreshPayments()
    }

    fun deleteExpense(expenseId: String) {
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
}
