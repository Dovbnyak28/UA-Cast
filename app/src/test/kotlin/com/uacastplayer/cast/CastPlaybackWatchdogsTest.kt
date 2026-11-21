package com.uacastplayer.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CastPlaybackWatchdogsTest {

    @Test
    fun `initial buffering never arms the mid-stream watchdog`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.state.status = ReceiverStatus.BUFFERING
        fixture.controller.onReceiverStatus(ReceiverStatus.BUFFERING)

        advancePastSustainedTimeout()

        assertTrue(fixture.failures.isEmpty())
    }

    @Test
    fun `buffering after playback fires the sustained watchdog`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.state.everPlayed = true
        fixture.state.status = ReceiverStatus.BUFFERING
        fixture.controller.onReceiverStatus(ReceiverStatus.BUFFERING)

        advancePastSustainedTimeout()

        assertEquals(
            listOf(CastWatchdogFailure.SustainedBuffering(SUSTAINED_MILLIS, CastDeliveryMode.Proxy)),
            fixture.failures,
        )
    }

    @Test
    fun `a real idle callback cancels the synthetic load stall`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.controller.watchLoad(fixture.state.generation, fixture.state.streamUrl)
        runCurrent()

        fixture.state.status = ReceiverStatus.IDLE
        fixture.controller.onReceiverStatus(ReceiverStatus.IDLE)
        advancePastStallTick()

        assertTrue(fixture.failures.isEmpty())
    }

    @Test
    fun `paused is a settled load and cancels the stall timer`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.controller.watchLoad(fixture.state.generation, fixture.state.streamUrl)
        runCurrent()

        fixture.state.status = ReceiverStatus.PAUSED
        fixture.controller.onReceiverStatus(ReceiverStatus.PAUSED)
        advancePastStallTick()

        assertTrue(fixture.failures.isEmpty())
    }

    @Test
    fun `flowing proxy bytes keep waiting but the next silent tick fires`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.state.status = ReceiverStatus.BUFFERING
        fixture.controller.watchLoad(fixture.state.generation, fixture.state.streamUrl)
        runCurrent()

        fixture.state.bytesServed = 10
        advancePastStallTick()
        assertTrue(fixture.failures.isEmpty())

        advancePastStallTick()
        assertEquals(
            listOf(
                CastWatchdogFailure.LoadStall(
                    elapsedMillis = STALL_TICK_MILLIS * 2,
                    bytesDeliveredThisTick = 0,
                    receiverStatus = ReceiverStatus.BUFFERING,
                    deliveryMode = CastDeliveryMode.Proxy,
                ),
            ),
            fixture.failures,
        )
    }

    @Test
    fun `a superseded generation quietly retires its watchdog`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.controller.watchLoad(fixture.state.generation, fixture.state.streamUrl)
        runCurrent()

        fixture.state.generation++
        advancePastStallTick()

        assertTrue(fixture.failures.isEmpty())
    }

    @Test
    fun `the first watchdog failure cancels the competing timer`() = runTest {
        val fixture = fixture(backgroundScope)
        fixture.state.everPlayed = true
        fixture.state.status = ReceiverStatus.BUFFERING
        fixture.controller.onReceiverStatus(ReceiverStatus.BUFFERING)
        fixture.controller.watchLoad(fixture.state.generation, fixture.state.streamUrl)
        runCurrent()

        advancePastStallTick()
        advancePastSustainedTimeout()

        assertEquals(1, fixture.failures.size)
        assertTrue(fixture.failures.single() is CastWatchdogFailure.LoadStall)
    }

    private fun TestScope.fixture(scope: CoroutineScope): Fixture {
        val state = WatchdogState()
        val failures = mutableListOf<CastWatchdogFailure>()
        val controller = CastPlaybackWatchdogs(
            scope = scope,
            inputs = CastWatchdogInputs(
                currentGeneration = { state.generation },
                activeStreamUrl = { state.streamUrl },
                receiverStatus = { state.status },
                deliveryMode = { CastDeliveryMode.Proxy },
                everReachedPlaying = { state.everPlayed },
                bytesServedToReceiver = { state.bytesServed },
            ),
            onFailure = failures::add,
            timing = CastWatchdogTiming(
                sustainedBufferingMillis = SUSTAINED_MILLIS,
                stallTickMillis = STALL_TICK_MILLIS,
            ),
        )
        return Fixture(state, controller, failures)
    }

    private suspend fun TestScope.advancePastSustainedTimeout() {
        advanceTimeBy(SUSTAINED_MILLIS)
        runCurrent()
    }

    private suspend fun TestScope.advancePastStallTick() {
        advanceTimeBy(STALL_TICK_MILLIS)
        runCurrent()
    }

    private data class Fixture(
        val state: WatchdogState,
        val controller: CastPlaybackWatchdogs,
        val failures: MutableList<CastWatchdogFailure>,
    )

    private data class WatchdogState(
        var generation: Long = 1,
        var streamUrl: String = "https://example.com/live.ts",
        var status: ReceiverStatus = ReceiverStatus.IDLE,
        var everPlayed: Boolean = false,
        var bytesServed: Long = 0,
    )

    private companion object {
        const val SUSTAINED_MILLIS = 1_000L
        const val STALL_TICK_MILLIS = 100L
    }
}
