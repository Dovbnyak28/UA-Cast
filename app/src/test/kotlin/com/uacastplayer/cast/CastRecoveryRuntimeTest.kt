package com.uacastplayer.cast

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CastRecoveryRuntimeTest {
    private val channel = CastChannel(0, "https://example.test/live", "Live")
    private var active: CastChannel? = channel
    private val loaded = mutableListOf<CastChannel>()

    @Test
    fun `playing cancels the delayed reload without resetting an exhausted budget`() = runTest {
        val runtime = CastRecoveryRuntime(backgroundScope, { active }, loaded::add)
        runtime.schedule(channel, CastRecoveryDecision.Reload(CastRecoveryPolicy.MAX_TOTAL_ATTEMPTS, 2_000))
        runtime.onStatus(ReceiverStatus.PLAYING, 0)
        advanceTimeBy(3_000)
        assertEquals(emptyList<CastChannel>(), loaded)
        runtime.onStatus(ReceiverStatus.IDLE, 500)
        assertEquals(CastRecoveryDecision.GiveUp, runtime.decisionFor(IdleReason.ERROR, false, false))
    }

    @Test
    fun `changing credentials for the same URL does not reload a stale channel`() = runTest {
        val runtime = CastRecoveryRuntime(backgroundScope, { active }, loaded::add)
        runtime.schedule(channel, CastRecoveryDecision.Reload(1, 2_000))
        active = channel.copy(userAgent = "Updated")
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(emptyList<CastChannel>(), loaded)
    }

    @Test
    fun `reset and suspension cancel reloads even after A B A`() = runTest {
        val runtime = CastRecoveryRuntime(backgroundScope, { active }, loaded::add)
        runtime.schedule(channel, CastRecoveryDecision.Reload(1, 2_000))
        active = channel.copy(index = 1)
        runtime.reset()
        active = channel
        advanceTimeBy(2_001)
        assertEquals(emptyList<CastChannel>(), loaded)
        runtime.schedule(channel, CastRecoveryDecision.Reload(1, 2_000))
        runtime.cancel()
        advanceTimeBy(2_001)
        assertEquals(emptyList<CastChannel>(), loaded)
    }

    @Test
    fun `replacement schedules one reload and healthy playback grants a new budget`() = runTest {
        val runtime = CastRecoveryRuntime(backgroundScope, { active }, loaded::add)
        runtime.schedule(channel, CastRecoveryDecision.Reload(1, 2_000))
        runtime.schedule(channel, CastRecoveryDecision.Reload(2, 2_000))
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(listOf(channel), loaded)
        runtime.onStatus(ReceiverStatus.PLAYING, 3_000)
        runtime.onStatus(ReceiverStatus.IDLE, 63_000)
        assertEquals(CastRecoveryDecision.Reload(1, 2_000), runtime.decisionFor(IdleReason.ERROR, false, false))
    }
}
