package com.uacastplayer.playlist

/**
 * Decides whether a failed playlist download deserves another attempt. Only transient failures
 * (network errors, or a 5xx from the server) are retried; a 4xx means the request itself is
 * wrong and retrying won't help.
 */
object HttpRetryPolicy {

    const val MAX_ATTEMPTS = 3

    private const val BASE_DELAY_MILLIS = 500L
    private const val MAX_DELAY_MILLIS = 4000L
    private const val MAX_BACKOFF_SHIFT = 10
    private const val HTTP_SERVER_ERROR_MIN = 500
    private const val HTTP_SERVER_ERROR_MAX = 599

    fun shouldRetry(attemptNumber: Int, isNetworkError: Boolean, httpStatusCode: Int? = null): Boolean {
        val isTransientHttpFailure =
            httpStatusCode != null && httpStatusCode in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX
        return attemptNumber < MAX_ATTEMPTS && (isNetworkError || isTransientHttpFailure)
    }

    /**
     * How long to wait before making attempt [attemptNumber] (1-based) - the first attempt is
     * never delayed, and each retry after that backs off exponentially from [BASE_DELAY_MILLIS],
     * capped at [MAX_DELAY_MILLIS] so a flaky origin can't stall playback indefinitely.
     */
    fun delayBeforeAttemptMillis(attemptNumber: Int): Long {
        if (attemptNumber <= 1) return 0L
        val shift = (attemptNumber - 2).coerceIn(0, MAX_BACKOFF_SHIFT)
        return (BASE_DELAY_MILLIS * (1L shl shift)).coerceAtMost(MAX_DELAY_MILLIS)
    }
}
