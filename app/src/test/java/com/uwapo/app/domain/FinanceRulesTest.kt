package com.uwapo.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceRulesTest {
    @Test fun outstandingSupportsPartialPayment() { assertEquals(5000L, FinanceRules.outstanding(10000L, 5000L)) }
    @Test fun outstandingNeverGoesBelowZero() { assertEquals(0L, FinanceRules.outstanding(10000L, 12000L)) }
    @Test fun collectionRateIsBoundedAndHandlesNoExpectedAmount() {
        assertEquals(50, FinanceRules.collectionRatePercent(10000L, 5000L))
        assertEquals(100, FinanceRules.collectionRatePercent(10000L, 12000L))
        assertEquals(0, FinanceRules.collectionRatePercent(0L, 2000L))
    }
    @Test fun contributionMustBePositive() {
        assertTrue(FinanceRules.validContribution(1L))
        assertFalse(FinanceRules.validContribution(0L))
        assertFalse(FinanceRules.validContribution(-1L))
    }
}
