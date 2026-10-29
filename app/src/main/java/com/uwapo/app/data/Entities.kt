package com.uwapo.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A batch is one contribution year (e.g. 2026). Every batch owns its own members:
 * a member row belongs to exactly one batch and is never shared with another one.
 */
@Entity(tableName = "batches")
data class BatchEntity(
    @PrimaryKey val year: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "members",
    indices = [
        Index(value = ["batchYear", "memberNo"], unique = true),
        Index("batchYear"),
        Index("status")
    ]
)
data class MemberEntity(
    @PrimaryKey val id: String,
    val memberNo: String,
    val fullName: String,
    val phone: String = "",
    val joinedAt: Long = System.currentTimeMillis(),
    val status: String = "active",
    val syncStatus: String = "local",
    val notes: String = "",
    val batchYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
)

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(entity = MemberEntity::class, parentColumns = ["id"], childColumns = ["memberId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("memberId"), Index("paidAt"), Index(value = ["requestId"], unique = true)]
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val amountTzs: Long,
    val periodYear: Int,
    val periodMonth: Int,
    val paidAt: Long = System.currentTimeMillis(),
    val method: String = "cash",
    val receiptNo: String,
    val requestId: String = java.util.UUID.randomUUID().toString(),
    val syncStatus: String = "local"
)

@Entity(tableName = "expenses", indices = [Index("expenseDate"), Index("syncStatus")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val category: String,
    val description: String,
    val amountTzs: Long,
    val expenseDate: Long = System.currentTimeMillis(),
    val accountName: String = "Cash",
    val status: String = "pending_approval",
    val syncStatus: String = "local",
    val receiptImagePath: String = ""
)
