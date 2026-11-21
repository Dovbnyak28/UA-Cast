package com.uacastplayer.icons

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconFailurePolicyTest {

    @Test
    fun `404 is a permanent failure`() {
        assertTrue(IconFailurePolicy.isPermanentFailure(404, isNetworkError = false))
    }

    @Test
    fun `500 is not a permanent failure`() {
        assertFalse(IconFailurePolicy.isPermanentFailure(500, isNetworkError = false))
    }

    @Test
    fun `a network error is never a permanent failure regardless of status code`() {
        assertFalse(IconFailurePolicy.isPermanentFailure(404, isNetworkError = true))
    }

    @Test
    fun `null status code with no network error is not permanent`() {
        assertFalse(IconFailurePolicy.isPermanentFailure(null, isNetworkError = false))
    }

    // Regression guard: a 429 (rate limit) is inherently temporary - the origin is asking to be
    // retried later, not saying the resource is gone. Blacklisting it for 7 days like a real 404
    // would keep an otherwise-working icon broken long after the rate limit clears.
    @Test
    fun `429 rate limit is not a permanent failure`() {
        assertFalse(IconFailurePolicy.isPermanentFailure(429, isNetworkError = false))
    }

    @Test
    fun `permanent failure record is not expired before 7 days`() {
        val record = FailureRecord(recordedAtMillis = 0L, isPermanent = true)
        assertFalse(IconFailurePolicy.isExpired(record, nowMillis = IconFailurePolicy.PERMANENT_TTL_MILLIS - 1))
    }

    @Test
    fun `permanent failure record expires after 7 days`() {
        val record = FailureRecord(recordedAtMillis = 0L, isPermanent = true)
        assertTrue(IconFailurePolicy.isExpired(record, nowMillis = IconFailurePolicy.PERMANENT_TTL_MILLIS + 1))
    }

    @Test
    fun `transient failure record expires after 1 hour`() {
        val record = FailureRecord(recordedAtMillis = 0L, isPermanent = false)
        assertFalse(IconFailurePolicy.isExpired(record, nowMillis = IconFailurePolicy.TRANSIENT_TTL_MILLIS - 1))
        assertTrue(IconFailurePolicy.isExpired(record, nowMillis = IconFailurePolicy.TRANSIENT_TTL_MILLIS + 1))
    }

    @Test
    fun `shouldSkip is false when there is no record`() {
        assertFalse(IconFailurePolicy.shouldSkip(null, nowMillis = 0L))
    }

    @Test
    fun `shouldSkip is true for a fresh record and false once expired`() {
        val record = FailureRecord(recordedAtMillis = 0L, isPermanent = false)
        assertTrue(IconFailurePolicy.shouldSkip(record, nowMillis = 100L))
        assertFalse(IconFailurePolicy.shouldSkip(record, nowMillis = IconFailurePolicy.TRANSIENT_TTL_MILLIS + 1))
    }

    @Test
    fun `correcting the clock must invalidate future records of both kinds`() {
        for (permanent in listOf(true, false)) {
            val record = FailureRecord(recordedAtMillis = 10_000L, isPermanent = permanent)
            assertTrue(IconFailurePolicy.isExpired(record, nowMillis = 9_000L))
            assertFalse(IconFailurePolicy.shouldSkip(record, nowMillis = 9_000L))
        }
    }

    @Test
    fun `negative persisted timestamps are invalid rather than overflowing the age`() {
        for (permanent in listOf(true, false)) {
            assertTrue(IconFailurePolicy.isExpired(FailureRecord(-1L, permanent), nowMillis = 0))
            assertTrue(IconFailurePolicy.isExpired(FailureRecord(Long.MIN_VALUE, permanent), nowMillis = 100))
        }
    }

    @Test
    fun `a very large future timestamp cannot blacklist a URL indefinitely`() {
        assertTrue(IconFailurePolicy.isExpired(FailureRecord(Long.MAX_VALUE, true), nowMillis = 100))
    }

    @Test
    fun `valid extreme timestamps do not overflow expiry arithmetic`() {
        assertTrue(IconFailurePolicy.isExpired(FailureRecord(0, true), nowMillis = Long.MAX_VALUE))
        assertFalse(IconFailurePolicy.isExpired(FailureRecord(Long.MAX_VALUE, true), nowMillis = Long.MAX_VALUE))
        assertFalse(IconFailurePolicy.isExpired(FailureRecord(Long.MAX_VALUE - 1, false), nowMillis = Long.MAX_VALUE))
    }

    @Test
    fun `records stay active exactly at their TTL and expire one millisecond later`() {
        for (permanent in listOf(true, false)) {
            val ttl = if (permanent) IconFailurePolicy.PERMANENT_TTL_MILLIS else IconFailurePolicy.TRANSIENT_TTL_MILLIS
            val record = FailureRecord(recordedAtMillis = 100L, isPermanent = permanent)
            assertFalse(IconFailurePolicy.isExpired(record, nowMillis = 100L + ttl))
            assertTrue(IconFailurePolicy.isExpired(record, nowMillis = 101L + ttl))
        }
    }
}
