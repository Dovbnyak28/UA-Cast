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
}
