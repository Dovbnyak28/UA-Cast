package com.uacastplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StallRetryPolicyTest {

    @Test
    fun `delay sequence escalates 2s 4s 8s 16s then steady 30s`() {
        var state = StallRetryPolicy.State()
        val delays = mutableListOf<Long>()
        repeat(6) {
            val decision = StallRetryPolicy.onStall(state) as StallRetryPolicy.Decision.Retry
            delays += decision.delayMillis
            state = decision.newState
        }
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), delays)
    }

    @Test
    fun `a permanently stalled stream receives exactly eight attempts`() {
        var state = StallRetryPolicy.State()
        repeat(StallRetryPolicy.MAX_ATTEMPTS) {
            val decision = StallRetryPolicy.onStall(state) as StallRetryPolicy.Decision.Retry
            state = decision.newState
        }
        assertEquals(StallRetryPolicy.MAX_ATTEMPTS, state.attempt)
        assertEquals(StallRetryPolicy.Decision.GiveUp, StallRetryPolicy.onStall(state))
    }

    @Test
    fun `backoff resets after a minute of observed forward progress`() {
        var state = StallRetryPolicy.State(attempt = 5)
        for (second in 0L..60L) {
            state = StallRetryPolicy.onPlaybackSample(second * 1_000L, isAdvancing = true, state)
        }
        val decision = StallRetryPolicy.onStall(state) as StallRetryPolicy.Decision.Retry
        assertEquals(2_000L, decision.delayMillis)
        assertEquals(1, decision.newState.attempt)
    }

    @Test
    fun `brief progress followed by buffering does not reset the budget`() {
        var state = StallRetryPolicy.State(attempt = 1)
        state = StallRetryPolicy.onPlaybackSample(0L, isAdvancing = true, state)
        state = StallRetryPolicy.onPlaybackSample(59_999L, isAdvancing = true, state)
        state = StallRetryPolicy.onPlaybackSample(60_000L, isAdvancing = false, state)
        state = StallRetryPolicy.onPlaybackSample(120_000L, isAdvancing = true, state)
        val decision = StallRetryPolicy.onStall(state) as StallRetryPolicy.Decision.Retry
        assertEquals(4_000L, decision.delayMillis)
        assertEquals(2, decision.newState.attempt)
    }

    @Test
    fun `minutes spent buffering cannot replenish an exhausted budget`() {
        val exhausted = StallRetryPolicy.State(attempt = StallRetryPolicy.MAX_ATTEMPTS)
        val afterWait = StallRetryPolicy.onPlaybackSample(600_000L, isAdvancing = false, exhausted)
        assertEquals(StallRetryPolicy.Decision.GiveUp, StallRetryPolicy.onStall(afterWait))
    }

    @Test
    fun `recovery kind is light for the first two attempts and heavy on the third`() {
        assertEquals(StallRecoveryKind.LIGHT, StallRetryPolicy.recoveryKindFor(1))
        assertEquals(StallRecoveryKind.LIGHT, StallRetryPolicy.recoveryKindFor(2))
        assertEquals(StallRecoveryKind.HEAVY, StallRetryPolicy.recoveryKindFor(3))
    }

    @Test
    fun `heavy recovery repeats every third attempt`() {
        assertEquals(StallRecoveryKind.LIGHT, StallRetryPolicy.recoveryKindFor(4))
        assertEquals(StallRecoveryKind.LIGHT, StallRetryPolicy.recoveryKindFor(5))
        assertEquals(StallRecoveryKind.HEAVY, StallRetryPolicy.recoveryKindFor(6))
        assertEquals(StallRecoveryKind.HEAVY, StallRetryPolicy.recoveryKindFor(9))
    }
}
