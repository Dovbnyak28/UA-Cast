package com.uacastplayer.player

import com.uacastplayer.playlist.M3uChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStallRecoveryRuntimeTest {
    private val session = PlayerSessionStateMachine().apply {
        start(listOf(M3uChannel(displayName = "Live", streamUrl = "https://example.test/live")), 0, false)
    }
    private var wanted = true
    private var local = true
    private val actions = mutableListOf<StallRecoveryKind>()

    private fun observation(now: Long) = PlayerStallObservation(
        tick = StallDetectionPolicy.Tick(now, 0, StallDetectionPolicy.PlaybackPhase.BUFFERING, wanted, true),
        thresholdMillis = 0,
        mayRecover = local,
    )

    @Test
    fun `repeated starts do not duplicate the sampler or its recovery`() = runTest {
        val runtime = PlayerStallRecovery(
            backgroundScope, session, { observation(testScheduler.currentTime) }, {}, actions::add,
        )
        repeat(5) { runtime.start() }
        advanceTimeBy(4_001)
        runCurrent()
        assertEquals(listOf(StallRecoveryKind.LIGHT), actions)
        runtime.stop()
    }

    @Test
    fun `pause and remote handoff invalidate an already scheduled action`() = runTest {
        val runtime = PlayerStallRecovery(
            backgroundScope, session, { observation(testScheduler.currentTime) }, {}, actions::add,
        )
        runtime.start()
        advanceTimeBy(2_001)
        wanted = false
        advanceTimeBy(2_001)
        assertEquals(emptyList<StallRecoveryKind>(), actions)
        wanted = true
        local = false
        runtime.perform(3)
        assertEquals(emptyList<StallRecoveryKind>(), actions)
        runtime.stop()
    }

    @Test
    fun `stop cancels both jobs and a later start has one fresh sampler`() = runTest {
        val runtime = PlayerStallRecovery(
            backgroundScope, session, { observation(testScheduler.currentTime) }, {}, actions::add,
        )
        runtime.start()
        advanceTimeBy(2_001)
        runtime.stop()
        advanceTimeBy(100_000)
        assertEquals(emptyList<StallRecoveryKind>(), actions)
        runtime.start()
        advanceTimeBy(4_001)
        assertEquals(listOf(StallRecoveryKind.LIGHT), actions)
        runtime.stop()
    }

    @Test
    fun `channel replacement cancels an old action even if its URL is selected again`() = runTest {
        val runtime = PlayerStallRecovery(
            backgroundScope, session, { observation(testScheduler.currentTime) }, {}, actions::add,
        )
        runtime.start()
        advanceTimeBy(2_001)
        runtime.cancel()
        runtime.cancel()
        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(emptyList<StallRecoveryKind>(), actions)
        runtime.stop()
    }
}
