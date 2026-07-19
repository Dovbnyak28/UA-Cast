package com.uacastplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveWindowRecoveryPolicyTest {

    @Test
    fun `recovers the first time with no history`() {
        val decision = LiveWindowRecoveryPolicy.onBehindLiveWindow(nowMillis = 0L, history = emptyList())
        assertEquals(LiveWindowRecoveryPolicy.Decision.Recover(listOf(0L)), decision)
    }

    @Test
    fun `recovers up to three times within the window`() {
        var history = emptyList<Long>()
        var decision = LiveWindowRecoveryPolicy.onBehindLiveWindow(1_000L, history)
        check(decision is LiveWindowRecoveryPolicy.Decision.Recover)
        history = decision.newHistory

        decision = LiveWindowRecoveryPolicy.onBehindLiveWindow(2_000L, history)
        check(decision is LiveWindowRecoveryPolicy.Decision.Recover)
        history = decision.newHistory

        decision = LiveWindowRecoveryPolicy.onBehindLiveWindow(3_000L, history)
        check(decision is LiveWindowRecoveryPolicy.Decision.Recover)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), decision.newHistory)
    }

    @Test
    fun `gives up on the fourth occurrence within 60s`() {
        var history = listOf(1_000L, 2_000L, 3_000L)
        val decision = LiveWindowRecoveryPolicy.onBehindLiveWindow(4_000L, history)
        assertEquals(LiveWindowRecoveryPolicy.Decision.GiveUp, decision)
    }

    @Test
    fun `old occurrences outside the 60s window don't count toward the limit`() {
        val history = listOf(0L, 1_000L, 2_000L)
        val decision = LiveWindowRecoveryPolicy.onBehindLiveWindow(nowMillis = 62_000L, history = history)
        assertEquals(LiveWindowRecoveryPolicy.Decision.Recover(listOf(62_000L)), decision)
    }

    @Test
    fun `a mix of expired and recent occurrences only counts the recent ones`() {
        // 0L is 61s before "now" (61_000L) - already outside the window - only 30_000L and 45_000L
        // count, so this is the third recovery within the window and should still succeed.
        val history = listOf(0L, 30_000L, 45_000L)
        val decision = LiveWindowRecoveryPolicy.onBehindLiveWindow(nowMillis = 61_000L, history = history)
        assertEquals(LiveWindowRecoveryPolicy.Decision.Recover(listOf(30_000L, 45_000L, 61_000L)), decision)
    }
}
