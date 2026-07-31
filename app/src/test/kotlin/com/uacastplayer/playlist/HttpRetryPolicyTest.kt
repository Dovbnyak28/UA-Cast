package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRetryPolicyTest {

    @Test
    fun `retries on network errors`() {
        assertTrue(HttpRetryPolicy.shouldRetry(attemptNumber = 1, isNetworkError = true, httpStatusCode = null))
    }

    @Test
    fun `retries on 5xx server errors`() {
        assertTrue(HttpRetryPolicy.shouldRetry(attemptNumber = 1, isNetworkError = false, httpStatusCode = 503))
    }

    @Test
    fun `does not retry on 4xx client errors`() {
        assertFalse(HttpRetryPolicy.shouldRetry(attemptNumber = 1, isNetworkError = false, httpStatusCode = 404))
    }

    @Test
    fun `does not retry on success status codes`() {
        assertFalse(HttpRetryPolicy.shouldRetry(attemptNumber = 1, isNetworkError = false, httpStatusCode = 200))
    }

    @Test
    fun `does not retry once max attempts is reached even for a retryable error`() {
        assertFalse(
            HttpRetryPolicy.shouldRetry(
                attemptNumber = HttpRetryPolicy.MAX_ATTEMPTS,
                isNetworkError = true,
                httpStatusCode = null,
            )
        )
    }

    @Test
    fun `does not retry with no status code and no network error`() {
        assertFalse(HttpRetryPolicy.shouldRetry(attemptNumber = 1, isNetworkError = false, httpStatusCode = null))
    }

    @Test
    fun `the first attempt has no delay`() {
        assertEquals(0L, HttpRetryPolicy.delayBeforeAttemptMillis(1))
    }

    @Test
    fun `delay doubles with each subsequent attempt`() {
        assertEquals(500L, HttpRetryPolicy.delayBeforeAttemptMillis(2))
        assertEquals(1000L, HttpRetryPolicy.delayBeforeAttemptMillis(3))
        assertEquals(2000L, HttpRetryPolicy.delayBeforeAttemptMillis(4))
    }

    @Test
    fun `delay is capped rather than growing without bound`() {
        assertEquals(4000L, HttpRetryPolicy.delayBeforeAttemptMillis(5))
        assertEquals(4000L, HttpRetryPolicy.delayBeforeAttemptMillis(50))
    }

    @Test
    fun `an attempt number of zero or less has no delay`() {
        assertEquals(0L, HttpRetryPolicy.delayBeforeAttemptMillis(0))
        assertEquals(0L, HttpRetryPolicy.delayBeforeAttemptMillis(-1))
    }
}
