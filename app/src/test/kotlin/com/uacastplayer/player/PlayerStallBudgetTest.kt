package com.uacastplayer.player

import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerStallBudgetTest {
    private val machine = PlayerSessionStateMachine().apply {
        start(listOf(M3uChannel(displayName = "Live", streamUrl = "https://example.test/live.m3u8")), 0, false)
    }

    private fun buffering(now: Long) = StallDetectionPolicy.Tick(
        nowMillis = now,
        positionMs = 0L,
        phase = StallDetectionPolicy.PlaybackPhase.BUFFERING,
        playWhenReady = true,
        isLive = true,
    )

    @Test
    fun `long buffering gaps and brief READY callbacks cannot cause infinite retries`() {
        repeat(StallRetryPolicy.MAX_ATTEMPTS) { attempt ->
            val effect = machine.onStallTick(buffering(attempt * 90_000L), thresholdMillis = 0L)
            assertEquals(attempt + 1, (effect as PlayerSessionStateMachine.StallEffect.ScheduleRecovery).attempt)
            // Same edge as Media3's onIsPlaying callback. A momentary READY does not prove health.
            machine.onPlaybackConfirmed()
            machine.cancelStallRecovery(resetBudget = false)
        }
        assertEquals(
            PlayerSessionStateMachine.StallEffect.GiveUp,
            machine.onStallTick(buffering(900_000L), thresholdMillis = 0L),
        )
        machine.retryCurrent(wrapAround = false)
        val afterManualRetry = machine.onStallTick(buffering(901_000L), thresholdMillis = 0L)
        assertEquals(1, (afterManualRetry as PlayerSessionStateMachine.StallEffect.ScheduleRecovery).attempt)
    }

    @Test
    fun `grace covers the backoff plus the recovery instead of expiring before prepare`() {
        repeat(5) { attempt -> machine.onStallTick(buffering(attempt * 90_000L), thresholdMillis = 0L) }
        // Fifth recovery is scheduled at 360s, runs at 390s and gets 20s of prepare grace.
        assertEquals(
            PlayerSessionStateMachine.StallEffect.None,
            machine.onStallTick(buffering(385_000L), thresholdMillis = 0L),
        )
        assertEquals(
            PlayerSessionStateMachine.StallEffect.None,
            machine.onStallTick(buffering(409_999L), thresholdMillis = 0L),
        )
    }

    @Test
    fun `buffering under the stall threshold does not clear the recovery indicator`() {
        machine.onStallTick(buffering(0L), thresholdMillis = 8_000L)
        machine.onStallTick(buffering(8_000L), thresholdMillis = 8_000L)
        assertEquals(
            PlayerSessionStateMachine.StallEffect.None,
            machine.onStallTick(buffering(30_001L), thresholdMillis = 8_000L),
        )
        val advancing = buffering(32_001L).copy(positionMs = 2_000L, phase = StallDetectionPolicy.PlaybackPhase.READY)
        assertEquals(
            PlayerSessionStateMachine.StallEffect.ClearRecoveryIndicator,
            machine.onStallTick(advancing, thresholdMillis = 8_000L),
        )
    }
}
