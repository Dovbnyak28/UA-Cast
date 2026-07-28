package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class CastRecoveryPolicyTest {

    @Test
    fun `first failure reloads at attempt 1 with a 2s backoff`() {
        val decision = CastRecoveryPolicy.onReceiverIdle(
            idleReason = IdleReason.ERROR,
            isConfirmedIncompatible = false,
            attemptsSoFar = 0,
            selfInitiated = false,
        )
        assertEquals(CastRecoveryDecision.Reload(attempt = 1, backoffMillis = 2_000L), decision)
    }

    @Test
    fun `second failure reloads at attempt 2 with a 4s backoff`() {
        val decision = CastRecoveryPolicy.onReceiverIdle(
            idleReason = IdleReason.ERROR,
            isConfirmedIncompatible = false,
            attemptsSoFar = 1,
            selfInitiated = false,
        )
        assertEquals(CastRecoveryDecision.Reload(attempt = 2, backoffMillis = 4_000L), decision)
    }

    @Test
    fun `third failure reloads at attempt 3 with an 8s backoff`() {
        val decision = CastRecoveryPolicy.onReceiverIdle(
            idleReason = IdleReason.ERROR,
            isConfirmedIncompatible = false,
            attemptsSoFar = 2,
            selfInitiated = false,
        )
        assertEquals(CastRecoveryDecision.Reload(attempt = 3, backoffMillis = 8_000L), decision)
    }

    @Test
    fun `a fourth failure keeps reloading at a steady 30s backoff instead of giving up`() {
        val decision = CastRecoveryPolicy.onReceiverIdle(
            idleReason = IdleReason.ERROR,
            isConfirmedIncompatible = false,
            attemptsSoFar = 3,
            selfInitiated = false,
        )
        assertEquals(CastRecoveryDecision.Reload(attempt = 4, backoffMillis = 30_000L), decision)
    }

    @Test
    fun `a twentieth failure still reloads at 30s - the cast route never permanently gives up on its own`() {
        val decision = CastRecoveryPolicy.onReceiverIdle(
            idleReason = IdleReason.ERROR,
            isConfirmedIncompatible = false,
            attemptsSoFar = 19,
            selfInitiated = false,
        )
        assertEquals(CastRecoveryDecision.Reload(attempt = 20, backoffMillis = 30_000L), decision)
    }

    @Test
    fun `a confirmed incompatible verdict gives up even after many prior reload attempts`() {
        val decision = CastRecoveryPolicy.onReceiverIdle(
            idleReason = IdleReason.ERROR,
            isConfirmedIncompatible = true,
            attemptsSoFar = 10,
            selfInitiated = false,
        )
        assertEquals(CastRecoveryDecision.GiveUp, decision)
    }

    @Test
    fun `a confirmed incompatible verdict gives up immediately, even on the first failure`() {
        val decision = CastRecoveryPolicy.onReceiverIdle(
            idleReason = IdleReason.ERROR,
            isConfirmedIncompatible = true,
            attemptsSoFar = 0,
            selfInitiated = false,
        )
        assertEquals(CastRecoveryDecision.GiveUp, decision)
    }

    @Test
    fun `a self-initiated IDLE is ignored regardless of everything else`() {
        val decision = CastRecoveryPolicy.onReceiverIdle(
            idleReason = IdleReason.ERROR,
            isConfirmedIncompatible = true,
            attemptsSoFar = 5,
            selfInitiated = true,
        )
        assertEquals(CastRecoveryDecision.Ignore, decision)
    }

    @Test
    fun `IDLE with CANCELLED is ignored - not a failure worth recovering from`() {
        val decision = CastRecoveryPolicy.onReceiverIdle(
            idleReason = IdleReason.CANCELLED,
            isConfirmedIncompatible = false,
            attemptsSoFar = 0,
            selfInitiated = false,
        )
        assertEquals(CastRecoveryDecision.Ignore, decision)
    }

    @Test
    fun `IDLE with INTERRUPTED is ignored - not a failure worth recovering from`() {
        val decision = CastRecoveryPolicy.onReceiverIdle(
            idleReason = IdleReason.INTERRUPTED,
            isConfirmedIncompatible = false,
            attemptsSoFar = 0,
            selfInitiated = false,
        )
        assertEquals(CastRecoveryDecision.Ignore, decision)
    }

    @Test
    fun `FINISHED reloads just like ERROR - this app has no VOD end-of-content case`() {
        val decision = CastRecoveryPolicy.onReceiverIdle(
            idleReason = IdleReason.FINISHED,
            isConfirmedIncompatible = false,
            attemptsSoFar = 0,
            selfInitiated = false,
        )
        assertEquals(CastRecoveryDecision.Reload(attempt = 1, backoffMillis = 2_000L), decision)
    }

    @Test
    fun `under 60s of stable playing does not reset the attempt counter`() {
        assertEquals(false, CastRecoveryPolicy.shouldResetAttemptCounter(59_999L))
    }

    @Test
    fun `60s of stable playing resets the attempt counter`() {
        assertEquals(true, CastRecoveryPolicy.shouldResetAttemptCounter(60_000L))
    }

    @Test
    fun `well past 60s of stable playing still resets the attempt counter`() {
        assertEquals(true, CastRecoveryPolicy.shouldResetAttemptCounter(600_000L))
    }
}
