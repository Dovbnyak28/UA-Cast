package com.uacastplayer.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncompatibilityMemoryPolicyTest {

    @Test
    fun `record is not expired before 30 days`() {
        val record = IncompatibilityRecord(recordedAtMillis = 0L)
        assertFalse(IncompatibilityMemoryPolicy.isExpired(record, nowMillis = IncompatibilityMemoryPolicy.TTL_MILLIS - 1))
    }

    @Test
    fun `record expires after 30 days`() {
        val record = IncompatibilityRecord(recordedAtMillis = 0L)
        assertTrue(IncompatibilityMemoryPolicy.isExpired(record, nowMillis = IncompatibilityMemoryPolicy.TTL_MILLIS + 1))
    }

    @Test
    fun `no record means do not go straight to proxy`() {
        assertFalse(IncompatibilityMemoryPolicy.shouldGoStraightToProxy(null, nowMillis = 0L))
    }

    @Test
    fun `a fresh record means go straight to proxy`() {
        val record = IncompatibilityRecord(recordedAtMillis = 0L)
        assertTrue(IncompatibilityMemoryPolicy.shouldGoStraightToProxy(record, nowMillis = 100L))
    }

    @Test
    fun `an expired record no longer forces proxy`() {
        val record = IncompatibilityRecord(recordedAtMillis = 0L)
        val nowMillis = IncompatibilityMemoryPolicy.TTL_MILLIS + 1
        assertFalse(IncompatibilityMemoryPolicy.shouldGoStraightToProxy(record, nowMillis))
    }
}
