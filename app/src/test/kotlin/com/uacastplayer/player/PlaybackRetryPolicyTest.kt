package com.uacastplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRetryPolicyTest {

    @Test
    fun `gives up immediately on a non-transient error`() {
        val decision = PlaybackRetryPolicy.onError(RetryState(), PlaybackErrorType.OTHER)
        assertEquals(RetryDecision.GiveUp, decision)
    }

    @Test
    fun `retries a network error with linear backoff`() {
        val first = PlaybackRetryPolicy.onError(RetryState(), PlaybackErrorType.NETWORK)
        check(first is RetryDecision.Retry)
        assertEquals(1, first.newState.attempt)
        assertEquals(500L, first.delayMillis)

        val second = PlaybackRetryPolicy.onError(first.newState, PlaybackErrorType.NETWORK)
        check(second is RetryDecision.Retry)
        assertEquals(2, second.newState.attempt)
        assertEquals(1000L, second.delayMillis)
    }

    @Test
    fun `retries timeout and behind-live-window errors`() {
        assertTrue(PlaybackRetryPolicy.onError(RetryState(), PlaybackErrorType.TIMEOUT) is RetryDecision.Retry)
        assertTrue(
            PlaybackRetryPolicy.onError(RetryState(), PlaybackErrorType.BEHIND_LIVE_WINDOW) is RetryDecision.Retry
        )
    }

    @Test
    fun `gives up once the attempt budget is exhausted`() {
        var state = RetryState()
        repeat(PlaybackRetryPolicy.MAX_ATTEMPTS) {
            val decision = PlaybackRetryPolicy.onError(state, PlaybackErrorType.NETWORK)
            check(decision is RetryDecision.Retry)
            state = decision.newState
        }
        assertEquals(RetryDecision.GiveUp, PlaybackRetryPolicy.onError(state, PlaybackErrorType.NETWORK))
    }

    @Test
    fun `isPlaying resets the retry budget`() {
        val afterErrors = RetryState(attempt = 3)
        assertEquals(RetryState(attempt = 0), PlaybackRetryPolicy.onIsPlaying(afterErrors))
    }

    @Test
    fun `isPlaying is a no-op when the budget is already at zero`() {
        val fresh = RetryState()
        assertEquals(fresh, PlaybackRetryPolicy.onIsPlaying(fresh))
    }
}
