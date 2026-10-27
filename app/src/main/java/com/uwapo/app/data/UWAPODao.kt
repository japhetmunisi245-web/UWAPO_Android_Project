package com.uwapo.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UWAPODao {
    @Query("SELECT * FROM members ORDER BY fullName COLLATE NOCASE")
    fun observeMembers(): Flow<List<MemberEntity>>
    @Query("SELECT * FROM payments ORDER BY paidAt DESC")
    fun observePayments(): Flow<List<PaymentEntity>>
    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM members WHERE id = :id LIMIT 1")
    suspend fun getMember(id: String): MemberEntity?
    @Query("SELECT * FROM members WHERE memberNo = :memberNo LIMIT 1")
    suspend fun getMemberByNumber(memberNo: String): MemberEntity?
    @Query("SELECT * FROM payments WHERE requestId = :requestId LIMIT 1")
    suspend fun getPaymentByRequestId(requestId: String): PaymentEntity?
    @Query("SELECT * FROM payments WHERE memberId = :memberId ORDER BY periodYear DESC, periodMonth DESC")
    fun observeMemberPayments(memberId: String): Flow<List<PaymentEntity>>
    @Query("SELECT * FROM payments WHERE memberId = :memberId ORDER BY periodYear DESC, periodMonth DESC")
    suspend fun getMemberPaymentsSync(memberId: String): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMember(member: MemberEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemberIgnore(member: MemberEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: PaymentEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaymentIgnore(payment: PaymentEntity): Long
    @Upsert
    suspend fun upsertMember(member: MemberEntity)
    @Upsert
    suspend fun upsertPayment(payment: PaymentEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: ExpenseEntity)
    @Upsert
    suspend fun upsertExpense(expense: ExpenseEntity)

    @Query("UPDATE members SET phone = :phone, status = :status, notes = :notes WHERE id = :id")
    suspend fun updateMemberProfile(id: String, phone: String, status: String, notes: String)
    @Query("UPDATE expenses SET status = :status WHERE id = :id")
    suspend fun setExpenseLocalStatus(id: String, status: String)
    @Query("UPDATE expenses SET receiptImagePath = :imagePath WHERE id = :id")
    suspend fun setExpenseReceiptImage(id: String, imagePath: String)
    @Query("UPDATE payments SET syncStatus = 'reversed' WHERE id = :id")
    suspend fun markPaymentReversed(id: String)

    @Query("SELECT COALESCE(SUM(amountTzs), 0) FROM payments WHERE periodYear = :year AND periodMonth = :month AND syncStatus != 'reversed'")
    fun observeMonthlyTotal(year: Int, month: Int): Flow<Long>
    @Query("SELECT COALESCE(SUM(amountTzs), 0) FROM payments WHERE periodYear = :year AND syncStatus != 'reversed'")
    fun observeYearlyTotal(year: Int): Flow<Long>
    @Query("SELECT COUNT(*) FROM members WHERE status = 'active'")
    fun observeActiveMemberCount(): Flow<Int>
    @Query("SELECT COALESCE(SUM(amountTzs), 0) FROM payments WHERE syncStatus != 'reversed'")
    fun observeLifetimeTotal(): Flow<Long>
    @Query("SELECT COALESCE(SUM(amountTzs), 0) FROM expenses WHERE status = 'paid' OR status = 'approved'")
    fun observePaidExpensesTotal(): Flow<Long>

    @Query("SELECT * FROM members WHERE syncStatus != 'synced'")
    suspend fun pendingMembers(): List<MemberEntity>
    @Query("UPDATE members SET syncStatus = :syncStatus WHERE id = :id")
    suspend fun setMemberSyncStatus(id: String, syncStatus: String)

    @Query("SELECT * FROM payments WHERE syncStatus != 'synced' AND syncStatus != 'reversed'")
    suspend fun pendingPayments(): List<PaymentEntity>
    @Query("UPDATE payments SET syncStatus = :syncStatus, receiptNo = :receiptNo WHERE id = :id")
    suspend fun setPaymentSyncStatus(id: String, syncStatus: String, receiptNo: String)

    @Query("SELECT * FROM expenses WHERE syncStatus != 'synced'")
    suspend fun pendingExpenses(): List<ExpenseEntity>
    @Query("UPDATE expenses SET syncStatus = :syncStatus WHERE id = :id")
    suspend fun setExpenseSyncStatus(id: String, syncStatus: String)
}
