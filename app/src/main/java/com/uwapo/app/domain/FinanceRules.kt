package com.uwapo.app.domain

object FinanceRules {
    fun outstanding(expectedTzs: Long, allocatedTzs: Long): Long = (expectedTzs - allocatedTzs).coerceAtLeast(0L)
    fun collectionRatePercent(expectedTzs: Long, collectedTzs: Long): Int {
        if (expectedTzs <= 0L) return 0
        return ((collectedTzs.coerceAtLeast(0L) * 100L) / expectedTzs).coerceAtMost(100L).toInt()
    }
    fun validContribution(amountTzs: Long): Boolean = amountTzs > 0L
}
