package com.uacastplayer.player

enum class PlaybackErrorType { NETWORK, TIMEOUT, BEHIND_LIVE_WINDOW, OTHER }

data class RetryState(val attempt: Int = 0)

sealed class RetryDecision {
    data class Retry(val delayMillis: Long, val newState: RetryState) : RetryDecision()
    data object GiveUp : RetryDecision()
}

/**
 * Governs automatic recovery from transient playback errors. Only network/timeout/behind-live-
 * window errors are retried (anything else is almost certainly not going to fix itself); the
 * retry budget resets the moment playback is confirmed healthy again via [onIsPlaying].
 */
object PlaybackRetryPolicy {

    const val MAX_ATTEMPTS = 4
    private const val BASE_DELAY_MILLIS = 500L

    fun onError(state: RetryState, errorType: PlaybackErrorType): RetryDecision {
        if (errorType == PlaybackErrorType.OTHER) return RetryDecision.GiveUp
        if (state.attempt >= MAX_ATTEMPTS) return RetryDecision.GiveUp

        val nextAttempt = state.attempt + 1
        val delay = BASE_DELAY_MILLIS * nextAttempt
        return RetryDecision.Retry(delayMillis = delay, newState = RetryState(attempt = nextAttempt))
    }

    fun onIsPlaying(state: RetryState): RetryState =
        if (state.attempt == 0) state else RetryState()
}
