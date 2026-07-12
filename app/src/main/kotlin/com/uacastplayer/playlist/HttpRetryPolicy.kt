package com.uacastplayer.playlist

/**
 * Decides whether a failed playlist download deserves another attempt. Only transient failures
 * (network errors, or a 5xx from the server) are retried; a 4xx means the request itself is
 * wrong and retrying won't help.
 */
object HttpRetryPolicy {

    const val MAX_ATTEMPTS = 3

    fun shouldRetry(attemptNumber: Int, isNetworkError: Boolean, httpStatusCode: Int? = null): Boolean {
        if (attemptNumber >= MAX_ATTEMPTS) return false
        if (isNetworkError) return true
        return httpStatusCode != null && httpStatusCode in 500..599
    }
}
