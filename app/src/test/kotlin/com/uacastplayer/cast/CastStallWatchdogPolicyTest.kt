package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class CastStallWatchdogPolicyTest {

    @Test
    fun `a tick with no bytes at all is a stall`() {
        val decision = CastStallWatchdogPolicy.decide(
            elapsedMillis = CastStallWatchdogPolicy.TICK_MILLIS,
            bytesDeliveredThisTick = 0,
            isPlaying = false,
        )
        assertEquals(CastStallDecision.Fire, decision)
    }

    /** The regression this policy exists for: the field capture had the receiver take a complete
     * 6.36MB segment 700ms before the old flat 4s timeout reloaded the load out from under it. */
    @Test
    fun `a receiver still pulling a segment is not stalled`() {
        val decision = CastStallWatchdogPolicy.decide(
            elapsedMillis = CastStallWatchdogPolicy.TICK_MILLIS,
            bytesDeliveredThisTick = 6_358_912,
            isPlaying = false,
        )
        assertEquals(CastStallDecision.KeepWaiting, decision)
    }

    @Test
    fun `even a trickle of bytes counts as progress`() {
        val decision = CastStallWatchdogPolicy.decide(
            elapsedMillis = CastStallWatchdogPolicy.TICK_MILLIS,
            bytesDeliveredThisTick = 1,
            isPlaying = false,
        )
        assertEquals(CastStallDecision.KeepWaiting, decision)
    }

    @Test
    fun `reaching PLAYING settles it, whatever the bytes say`() {
        val decision = CastStallWatchdogPolicy.decide(
            elapsedMillis = CastStallWatchdogPolicy.TICK_MILLIS,
            bytesDeliveredThisTick = 0,
            isPlaying = true,
        )
        assertEquals(CastStallDecision.Settled, decision)
    }

    /** A receiver that fetches forever but never plays - an unsupported codec it only discovers
     * after buffering - must not keep itself alive with its own fetching. */
    @Test
    fun `the ceiling fires even while bytes are still flowing`() {
        val decision = CastStallWatchdogPolicy.decide(
            elapsedMillis = CastStallWatchdogPolicy.MAX_WAIT_MILLIS,
            bytesDeliveredThisTick = 6_500_000,
            isPlaying = false,
        )
        assertEquals(CastStallDecision.Fire, decision)
    }

    @Test
    fun `the ceiling does not override an already-playing receiver`() {
        val decision = CastStallWatchdogPolicy.decide(
            elapsedMillis = CastStallWatchdogPolicy.MAX_WAIT_MILLIS * 2,
            bytesDeliveredThisTick = 0,
            isPlaying = true,
        )
        assertEquals(CastStallDecision.Settled, decision)
    }

    /** Direct mode has no proxy serving anything, so the byte delta is always zero and this must
     * behave exactly like the flat timeout it replaced - no mode flag, no drift between paths. */
    @Test
    fun `direct mode, where the proxy serves nothing, fires on the first tick`() {
        val decision = CastStallWatchdogPolicy.decide(
            elapsedMillis = CastStallWatchdogPolicy.TICK_MILLIS,
            bytesDeliveredThisTick = 0,
            isPlaying = false,
        )
        assertEquals(CastStallDecision.Fire, decision)
    }
}
