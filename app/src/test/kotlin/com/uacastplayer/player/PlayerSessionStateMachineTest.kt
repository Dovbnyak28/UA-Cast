package com.uacastplayer.player

import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSessionStateMachineTest {

    private fun channel(index: Int) = M3uChannel(
        displayName = "Channel $index",
        streamUrl = "https://example.test/$index.ts",
    )

    @Test
    fun `start resets previous channel history from an older playlist`() {
        val machine = PlayerSessionStateMachine()
        machine.start(listOf(channel(0), channel(1)), 0, wrapAround = true)
        machine.switchTo(1, wrapAround = true)
        assertEquals(0, machine.previousChannelIndex)

        val transition = machine.start(listOf(channel(2)), 0, wrapAround = true)

        assertNull(machine.previousChannelIndex)
        assertFalse(requireNotNull(transition).hasPreviousChannel)
    }

    @Test
    fun `switch resets a spent retry budget for the new channel`() {
        val machine = PlayerSessionStateMachine()
        machine.start(listOf(channel(0), channel(1)), 0, wrapAround = false)
        repeat(PlaybackRetryPolicy.MAX_ATTEMPTS) {
            assertTrue(
                machine.onPlaybackError(
                    PlaybackErrorType.NETWORK,
                    nowMillis = it.toLong(),
                    hasNetwork = true,
                    autoSkipDead = false,
                    wrapAround = false,
                ) is PlayerSessionStateMachine.PlaybackFailureEffect.Retry,
            )
        }
        assertTrue(
            machine.onPlaybackError(
                PlaybackErrorType.NETWORK,
                nowMillis = 10L,
                hasNetwork = true,
                autoSkipDead = false,
                wrapAround = false,
            ) is PlayerSessionStateMachine.PlaybackFailureEffect.Fatal,
        )

        machine.switchTo(1, wrapAround = false)

        assertTrue(
            machine.onPlaybackError(
                PlaybackErrorType.NETWORK,
                nowMillis = 11L,
                hasNetwork = true,
                autoSkipDead = false,
                wrapAround = false,
            ) is PlayerSessionStateMachine.PlaybackFailureEffect.Retry,
        )
    }

    @Test
    fun `behind live window budget belongs to one channel`() {
        val machine = PlayerSessionStateMachine()
        machine.start(listOf(channel(0), channel(1)), 0, wrapAround = false)
        repeat(3) { attempt ->
            val effect = machine.onPlaybackError(
                PlaybackErrorType.BEHIND_LIVE_WINDOW,
                nowMillis = attempt * 1_000L,
                hasNetwork = true,
                autoSkipDead = false,
                wrapAround = false,
            )
            assertTrue(effect is PlayerSessionStateMachine.PlaybackFailureEffect.RecoverLiveWindow)
        }

        machine.switchTo(1, wrapAround = false)

        assertEquals(
            PlayerSessionStateMachine.PlaybackFailureEffect.RecoverLiveWindow(1),
            machine.onPlaybackError(
                PlaybackErrorType.BEHIND_LIVE_WINDOW,
                nowMillis = 4_000L,
                hasNetwork = true,
                autoSkipDead = false,
                wrapAround = false,
            ),
        )
    }

    @Test
    fun `network outage retries current channel without marking it dead`() {
        val machine = PlayerSessionStateMachine()
        machine.start(listOf(channel(0), channel(1)), 0, wrapAround = true)
        repeat(PlaybackRetryPolicy.MAX_ATTEMPTS) {
            machine.onPlaybackError(
                PlaybackErrorType.NETWORK,
                nowMillis = it.toLong(),
                hasNetwork = false,
                autoSkipDead = true,
                wrapAround = true,
            )
        }

        assertEquals(
            PlayerSessionStateMachine.PlaybackFailureEffect.RetryWhenNetworkAvailable,
            machine.onPlaybackError(
                PlaybackErrorType.NETWORK,
                nowMillis = 10L,
                hasNetwork = false,
                autoSkipDead = true,
                wrapAround = true,
            ),
        )
        assertEquals(0, requireNotNull(machine.switchTo(0, wrapAround = true)).index)
    }

    @Test
    fun `dead channel auto skip returns a complete switch transition`() {
        val machine = PlayerSessionStateMachine()
        machine.start(listOf(channel(0), channel(1), channel(2)), 0, wrapAround = false)
        repeat(PlaybackRetryPolicy.MAX_ATTEMPTS) {
            machine.onPlaybackError(
                PlaybackErrorType.NETWORK,
                nowMillis = it.toLong(),
                hasNetwork = true,
                autoSkipDead = true,
                wrapAround = false,
            )
        }

        val effect = machine.onPlaybackError(
            PlaybackErrorType.NETWORK,
            nowMillis = 10L,
            hasNetwork = true,
            autoSkipDead = true,
            wrapAround = false,
        ) as PlayerSessionStateMachine.PlaybackFailureEffect.SwitchChannel

        assertEquals(1, effect.transition.index)
        assertEquals("Channel 1", effect.transition.channel.displayName)
        assertTrue(effect.transition.hasPreviousChannel)
        assertEquals(1, effect.skippedChannels)
        assertEquals(3, effect.totalChannels)
    }

    @Test
    fun `explicit retry restores current channel after it was marked dead`() {
        val machine = PlayerSessionStateMachine()
        machine.start(listOf(channel(0)), 0, wrapAround = false)
        repeat(PlaybackRetryPolicy.MAX_ATTEMPTS + 1) {
            machine.onPlaybackError(
                PlaybackErrorType.NETWORK,
                nowMillis = it.toLong(),
                hasNetwork = true,
                autoSkipDead = false,
                wrapAround = false,
            )
        }

        val transition = machine.retryCurrent(wrapAround = false)

        assertEquals(0, requireNotNull(transition).index)
        assertTrue(
            machine.onPlaybackError(
                PlaybackErrorType.NETWORK,
                nowMillis = 100L,
                hasNetwork = true,
                autoSkipDead = false,
                wrapAround = false,
            ) is PlayerSessionStateMachine.PlaybackFailureEffect.Retry,
        )
    }

    @Test
    fun `release clears current and previous channel`() {
        val machine = PlayerSessionStateMachine()
        machine.start(listOf(channel(0), channel(1)), 0, wrapAround = true)
        machine.switchTo(1, wrapAround = true)

        machine.release()

        assertFalse(machine.hasCurrentChannel)
        assertNull(machine.previousChannelIndex)
    }

    @Test
    fun `cancelled stall recovery restarts from the first attempt`() {
        val machine = PlayerSessionStateMachine()
        machine.start(listOf(channel(0)), 0, wrapAround = false)
        val stalled = StallDetectionPolicy.Tick(
            nowMillis = 1_000L,
            positionMs = 0L,
            phase = StallDetectionPolicy.PlaybackPhase.BUFFERING,
            playWhenReady = true,
            isLive = true,
        )

        val first = machine.onStallTick(stalled, thresholdMillis = 0L)
        assertEquals(1, (first as PlayerSessionStateMachine.StallEffect.ScheduleRecovery).attempt)

        machine.cancelStallRecovery()

        val afterCancellation = machine.onStallTick(stalled.copy(nowMillis = 2_000L), thresholdMillis = 0L)
        assertEquals(
            1,
            (afterCancellation as PlayerSessionStateMachine.StallEffect.ScheduleRecovery).attempt,
        )
    }
}
