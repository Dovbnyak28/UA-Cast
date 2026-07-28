package com.uacastplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StallRetryPolicyTest {

    @Test
    fun `delay sequence escalates 2s 4s 8s 16s then steady 30s`() {
        var state = StallRetryPolicy.State()
        var now = 0L
        val delays = mutableListOf<Long>()
        // Stall immediately after each previous recovery (well under the 60s reset window) so the
        // backoff keeps escalating instead of resetting.
        repeat(6) {
            val decision = StallRetryPolicy.onStall(now, state)
            delays += decision.delayMillis
            state = decision.newState
            now += 1_000L
        }
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), delays)
    }

    @Test
    fun `attempt number 20 still returns a retry, never a give-up`() {
        var state = StallRetryPolicy.State()
        var now = 0L
        repeat(20) {
            val decision = StallRetryPolicy.onStall(now, state)
            state = decision.newState
            now += 1_000L
        }
        assertEquals(20, state.attempt)
        // Steady-state delay, not some sentinel/give-up value - the policy has no GiveUp branch at all.
        assertEquals(30_000L, StallRetryPolicy.onStall(now, state).delayMillis)
    }

    @Test
    fun `backoff resets to the start after 60s of no further stalls`() {
        var state = StallRetryPolicy.State()
        state = StallRetryPolicy.onStall(0L, state).newState
        state = StallRetryPolicy.onStall(1_000L, state).newState
        // Second recovery attempt started at t=1000; the stream then plays cleanly for exactly the
        // 60s reset window before stalling again at t=61000.
        val decision = StallRetryPolicy.onStall(61_000L, state)
        assertEquals(2_000L, decision.delayMillis)
        assertEquals(1, decision.newState.attempt)
    }

    @Test
    fun `a stall just under the 60s window does not reset the backoff`() {
        var state = StallRetryPolicy.State()
        state = StallRetryPolicy.onStall(0L, state).newState
        val decision = StallRetryPolicy.onStall(59_999L, state)
        assertEquals(4_000L, decision.delayMillis)
        assertEquals(2, decision.newState.attempt)
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
