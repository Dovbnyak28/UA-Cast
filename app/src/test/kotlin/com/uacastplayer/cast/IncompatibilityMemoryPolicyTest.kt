package com.uacastplayer.cast

import org.junit.Assert.assertEquals
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

    @Test
    fun `keysToPrune drops only expired keys when under the size cap`() {
        val entries = mapOf("fresh" to 0L, "stale" to 0L)
        val nowMillis = IncompatibilityMemoryPolicy.TTL_MILLIS + 1
        // "fresh" was just re-recorded at nowMillis - TTL_MILLIS, i.e. not expired yet.
        val withFresh = entries + ("fresh" to nowMillis)

        val toPrune = IncompatibilityMemoryPolicy.keysToPrune(withFresh, nowMillis, maxEntries = 10)

        assertEquals(setOf("stale"), toPrune)
    }

    @Test
    fun `keysToPrune evicts the oldest survivors once over the size cap`() {
        val entries = mapOf("a" to 100L, "b" to 200L, "c" to 300L)

        val toPrune = IncompatibilityMemoryPolicy.keysToPrune(entries, nowMillis = 300L, maxEntries = 2)

        assertEquals(setOf("a"), toPrune)
    }

    @Test
    fun `keysToPrune prunes nothing when under both the TTL and the size cap`() {
        val entries = mapOf("a" to 100L, "b" to 200L)

        val toPrune = IncompatibilityMemoryPolicy.keysToPrune(entries, nowMillis = 200L, maxEntries = 10)

        assertTrue(toPrune.isEmpty())
    }
}
