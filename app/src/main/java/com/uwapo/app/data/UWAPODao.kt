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
    @Query("SELECT * FROM batches ORDER BY year DESC")
    fun observeBatches(): Flow<List<BatchEntity>>
    @Query("SELECT * FROM batches ORDER BY year DESC")
    suspend fun getAllBatchesSync(): List<BatchEntity>
    @Query("SELECT * FROM payments ORDER BY paidAt DESC")
    fun observePayments(): Flow<List<PaymentEntity>>
    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM members WHERE id = :id LIMIT 1")
    suspend fun getMember(id: String): MemberEntity?
    @Query("SELECT * FROM members WHERE batchYear = :batchYear AND memberNo = :memberNo LIMIT 1")
    suspend fun getMemberByNumber(batchYear: Int, memberNo: String): MemberEntity?
    @Query("SELECT * FROM members")
    suspend fun getAllMembersSync(): List<MemberEntity>
    @Query("SELECT * FROM members WHERE batchYear = :batchYear")
    suspend fun getMembersForBatchSync(batchYear: Int): List<MemberEntity>
    @Query("SELECT * FROM payments WHERE requestId = :requestId LIMIT 1")
    suspend fun getPaymentByRequestId(requestId: String): PaymentEntity?
    @Query("SELECT * FROM payments WHERE memberId = :memberId ORDER BY periodYear DESC, periodMonth DESC")
    fun observeMemberPayments(memberId: String): Flow<List<PaymentEntity>>
    @Query("SELECT * FROM payments WHERE memberId = :memberId ORDER BY periodYear DESC, periodMonth DESC")
    suspend fun getMemberPaymentsSync(memberId: String): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBatchIgnore(batch: BatchEntity): Long
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
    @Query("UPDATE members SET memberNo = :memberNo, fullName = :fullName, phone = :phone, status = :status, notes = :notes WHERE id = :id")
    suspend fun updateMemberDetails(id: String, memberNo: String, fullName: String, phone: String, status: String, notes: String)
    @Query("DELETE FROM payments WHERE memberId IN (SELECT id FROM members WHERE batchYear = :batchYear)")
    suspend fun deletePaymentsForBatch(batchYear: Int)
    @Query("DELETE FROM members WHERE batchYear = :batchYear")
    suspend fun deleteMembersForBatch(batchYear: Int)
    @Query("DELETE FROM batches WHERE year = :batchYear")
    suspend fun deleteBatchRow(batchYear: Int)
    /** Deletes a batch together with its own members and their payments. Other batches are untouched. */
    @androidx.room.Transaction
    suspend fun deleteBatch(batchYear: Int) {
        deletePaymentsForBatch(batchYear)
        deleteMembersForBatch(batchYear)
        deleteBatchRow(batchYear)
    }
    @Query("DELETE FROM payments WHERE memberId = :memberId")
    suspend fun deletePaymentsForMember(memberId: String)
    @Query("DELETE FROM members WHERE id = :id")
    suspend fun deleteMemberOnly(id: String)
    @androidx.room.Transaction
    suspend fun deleteMember(memberId: String) {
        deletePaymentsForMember(memberId)
        deleteMemberOnly(memberId)
    }
    @Query("UPDATE expenses SET status = :status WHERE id = :id")
    suspend fun setExpenseLocalStatus(id: String, status: String)
    @Query("UPDATE expenses SET receiptImagePath = :imagePath WHERE id = :id")
    suspend fun setExpenseReceiptImage(id: String, imagePath: String)
    @Query("UPDATE payments SET syncStatus = 'reversed' WHERE id = :id")
    suspend fun markPaymentReversed(id: String)
    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deletePayment(id: String)
    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: String)

    @Query("SELECT COALESCE(SUM(amountTzs), 0) FROM payments WHERE periodYear = :year AND periodMonth = :month AND syncStatus != 'reversed'")
    fun observeMonthlyTotal(year: Int, month: Int): Flow<Long>
    @Query("SELECT COALESCE(SUM(amountTzs), 0) FROM payments WHERE periodYear = :year AND syncStatus != 'reversed'")
    fun observeYearlyTotal(year: Int): Flow<Long>
    @Query("SELECT COUNT(*) FROM members WHERE status = 'active' AND batchYear = :batchYear")
    fun observeActiveMemberCount(batchYear: Int): Flow<Int>
    @Query("SELECT COALESCE(SUM(amountTzs), 0) FROM payments WHERE syncStatus != 'reversed'")
    fun observeLifetimeTotal(): Flow<Long>
    @Query("SELECT COALESCE(SUM(amountTzs), 0) FROM expenses WHERE status = 'paid' OR status = 'approved'")
    fun observePaidExpensesTotal(): Flow<Long>
}
