package com.uacastplayer.proxy

private const val MAX_ATTEMPTS = 3
private const val BASE_BACKOFF_MILLIS = 1_000L

/**
 * Backoff/attempt-limit policy for [RawTsRemuxSession][com.uacastplayer.data.cast.RawTsRemuxSession]'s
 * upstream reconnect after the origin connection drops mid-stream: 1s, 2s, 4s, giving up after
 * [MAX_ATTEMPTS] consecutive failures - a successful reconnect resets the attempt counter (see the
 * caller), so this only ever sees a fresh run of failures, not a long-running session's whole
 * history.
 */
object RemuxReconnectPolicy {

    sealed interface Decision {
        data class Retry(val delayMillis: Long, val nextAttempt: Int) : Decision
        data object GiveUp : Decision
    }

    /** [attempt] is how many consecutive reconnect attempts have already failed (0 for the first
     * attempt after a fresh disconnect). */
    fun onDisconnected(attempt: Int): Decision {
        if (attempt >= MAX_ATTEMPTS) return Decision.GiveUp
        val delayMillis = BASE_BACKOFF_MILLIS shl attempt
        return Decision.Retry(delayMillis = delayMillis, nextAttempt = attempt + 1)
    }
}
