package com.uacastplayer.player

import com.uacastplayer.data.prefs.BufferSize
import org.junit.Assert.assertEquals
import org.junit.Test

private const val THRESHOLD_MILLIS = 8_000L

class StallDetectionPolicyTest {

    private fun tick(
        nowMillis: Long,
        positionMs: Long,
        phase: StallDetectionPolicy.PlaybackPhase,
        playWhenReady: Boolean = true,
        isLive: Boolean = true,
    ) = StallDetectionPolicy.Tick(nowMillis, positionMs, phase, playWhenReady, isLive)

    @Test
    fun `normal playback with advancing position stays healthy`() {
        var state = StallDetectionPolicy.StallState.NONE
        var now = 0L
        var position = 0L
        repeat(10) {
            val result = StallDetectionPolicy.evaluate(
                tick(now, position, StallDetectionPolicy.PlaybackPhase.READY),
                state,
                THRESHOLD_MILLIS,
            )
            assertEquals(StallDetectionPolicy.Health.HEALTHY, result.health)
            state = result.state
            now += 2_000
            position += 2_000
        }
    }

    @Test
    fun `pausing is not a stall even with a frozen position`() {
        var state = StallDetectionPolicy.StallState.NONE
        state = StallDetectionPolicy.evaluate(
            tick(0L, 1_000L, StallDetectionPolicy.PlaybackPhase.READY),
            state,
            THRESHOLD_MILLIS,
        ).state

        var now = 2_000L
        repeat(10) {
            val result = StallDetectionPolicy.evaluate(
                tick(now, 1_000L, StallDetectionPolicy.PlaybackPhase.READY, playWhenReady = false),
                state,
                THRESHOLD_MILLIS,
            )
            assertEquals(StallDetectionPolicy.Health.HEALTHY, result.health)
            state = result.state
            now += 2_000
        }
    }

    @Test
    fun `buffering shorter than the threshold stays healthy`() {
        var state = StallDetectionPolicy.StallState.NONE
        var now = 0L
        // 3 ticks of buffering at 2s apart = 4s elapsed, under the 8s threshold.
        repeat(3) {
            val result = StallDetectionPolicy.evaluate(
                tick(now, 1_000L, StallDetectionPolicy.PlaybackPhase.BUFFERING),
                state,
                THRESHOLD_MILLIS,
            )
            assertEquals(StallDetectionPolicy.Health.HEALTHY, result.health)
            state = result.state
            now += 2_000
        }
    }

    @Test
    fun `buffering past the threshold is a real stall`() {
        var state = StallDetectionPolicy.StallState.NONE
        var now = 0L
        var lastHealth = StallDetectionPolicy.Health.HEALTHY
        // Ticks at 0, 2000, 4000, 6000, 8000 - the tick at 8000 hits the 8s threshold.
        repeat(5) {
            val result = StallDetectionPolicy.evaluate(
                tick(now, 1_000L, StallDetectionPolicy.PlaybackPhase.BUFFERING),
                state,
                THRESHOLD_MILLIS,
            )
            lastHealth = result.health
            state = result.state
            now += 2_000
        }
        assertEquals(StallDetectionPolicy.Health.STALLED, lastHealth)
    }

    @Test
    fun `a READY position that stops advancing past the threshold is a real stall`() {
        var state = StallDetectionPolicy.evaluate(
            tick(0L, 0L, StallDetectionPolicy.PlaybackPhase.READY),
            StallDetectionPolicy.StallState.NONE,
            THRESHOLD_MILLIS,
        ).state

        var now = 2_000L
        var lastHealth = StallDetectionPolicy.Health.HEALTHY
        // Position frozen at 0 from here on, ticking every 2s.
        repeat(5) {
            val result = StallDetectionPolicy.evaluate(
                tick(now, 0L, StallDetectionPolicy.PlaybackPhase.READY),
                state,
                THRESHOLD_MILLIS,
            )
            lastHealth = result.health
            state = result.state
            now += 2_000
        }
        assertEquals(StallDetectionPolicy.Health.STALLED, lastHealth)
    }

    @Test
    fun `position resuming clears an in-progress stall streak`() {
        var state = StallDetectionPolicy.evaluate(
            tick(0L, 0L, StallDetectionPolicy.PlaybackPhase.READY),
            StallDetectionPolicy.StallState.NONE,
            THRESHOLD_MILLIS,
        ).state
        // Frozen for 6s (under threshold).
        state = StallDetectionPolicy.evaluate(
            tick(2_000L, 0L, StallDetectionPolicy.PlaybackPhase.READY),
            state,
            THRESHOLD_MILLIS,
        ).state
        state = StallDetectionPolicy.evaluate(
            tick(4_000L, 0L, StallDetectionPolicy.PlaybackPhase.READY),
            state,
            THRESHOLD_MILLIS,
        ).state
        // Position resumes advancing - streak clears.
        state = StallDetectionPolicy.evaluate(
            tick(6_000L, 1_000L, StallDetectionPolicy.PlaybackPhase.READY),
            state,
            THRESHOLD_MILLIS,
        ).state
        assertEquals(null, state.stallStartMillis)

        // Frozen again for another 6s - if the earlier streak had carried over, this would already
        // be stalled; it shouldn't be, since the streak restarted at t=6000.
        state = StallDetectionPolicy.evaluate(
            tick(8_000L, 1_000L, StallDetectionPolicy.PlaybackPhase.READY),
            state,
            THRESHOLD_MILLIS,
        ).state
        val result = StallDetectionPolicy.evaluate(
            tick(10_000L, 1_000L, StallDetectionPolicy.PlaybackPhase.READY),
            state,
            THRESHOLD_MILLIS,
        )
        assertEquals(StallDetectionPolicy.Health.HEALTHY, result.health)
    }

    @Test
    fun `a non-live stream never stalls regardless of position`() {
        val result = StallDetectionPolicy.evaluate(
            tick(10_000L, 0L, StallDetectionPolicy.PlaybackPhase.BUFFERING, isLive = false),
            StallDetectionPolicy.StallState(stallStartMillis = 0L, previousPositionMs = 0L),
            THRESHOLD_MILLIS,
        )
        assertEquals(StallDetectionPolicy.Health.HEALTHY, result.health)
    }

    @Test
    fun `threshold tiers match the buffer size profile`() {
        assertEquals(8_000L, StallDetectionPolicy.thresholdMillisFor(BufferSize.SMALL))
        assertEquals(12_000L, StallDetectionPolicy.thresholdMillisFor(BufferSize.MEDIUM))
        assertEquals(20_000L, StallDetectionPolicy.thresholdMillisFor(BufferSize.LARGE))
    }

    @Test
    fun `buffering throughout the recovery grace period stays healthy and in-grace`() {
        // Grace is max(2x8000, 20000) = 20000, armed at t=0.
        var state = StallDetectionPolicy.afterRecovery(nowMillis = 0L, thresholdMillis = THRESHOLD_MILLIS)
        var now = 2_000L
        // Ticks up to t=18000 (< 20000 grace deadline) - every one must stay healthy and in-grace,
        // even though this is BUFFERING for the whole window (well past the 8s stall threshold) -
        // this is exactly the case that used to self-defeat the recovery it was supposed to give.
        repeat(8) {
            val result = StallDetectionPolicy.evaluate(
                tick(now, 1_000L, StallDetectionPolicy.PlaybackPhase.BUFFERING),
                state,
                THRESHOLD_MILLIS,
            )
            assertEquals(StallDetectionPolicy.Health.HEALTHY, result.health)
            assertEquals(true, result.inGracePeriod)
            state = result.state
            now += 2_000
        }
    }

    @Test
    fun `a stall after the grace period lapses is detected normally`() {
        var state = StallDetectionPolicy.afterRecovery(nowMillis = 0L, thresholdMillis = THRESHOLD_MILLIS)
        // Grace deadline is t=20000 - tick right at/after it with BUFFERING for a full new
        // threshold's worth (8s) to confirm detection resumes instead of staying suppressed forever.
        var now = 20_000L
        var lastResult = StallDetectionPolicy.evaluate(
            tick(now, 1_000L, StallDetectionPolicy.PlaybackPhase.BUFFERING),
            state,
            THRESHOLD_MILLIS,
        )
        state = lastResult.state
        assertEquals(false, lastResult.inGracePeriod)
        repeat(4) {
            now += 2_000
            lastResult = StallDetectionPolicy.evaluate(
                tick(now, 1_000L, StallDetectionPolicy.PlaybackPhase.BUFFERING),
                state,
                THRESHOLD_MILLIS,
            )
            state = lastResult.state
        }
        assertEquals(StallDetectionPolicy.Health.STALLED, lastResult.health)
        assertEquals(false, lastResult.inGracePeriod)
    }

    @Test
    fun `grace period uses the 20s floor for a small threshold that would otherwise be shorter`() {
        // 2x SMALL(8000) = 16000, below the 20000 floor - the floor must win.
        val state = StallDetectionPolicy.afterRecovery(nowMillis = 0L, thresholdMillis = THRESHOLD_MILLIS)
        val result = StallDetectionPolicy.evaluate(
            tick(17_000L, 1_000L, StallDetectionPolicy.PlaybackPhase.BUFFERING),
            state,
            THRESHOLD_MILLIS,
        )
        assertEquals(StallDetectionPolicy.Health.HEALTHY, result.health)
        assertEquals(true, result.inGracePeriod)
    }

    @Test
    fun `a normal healthy tick outside of any grace period is not flagged in-grace`() {
        val result = StallDetectionPolicy.evaluate(
            tick(0L, 0L, StallDetectionPolicy.PlaybackPhase.READY),
            StallDetectionPolicy.StallState.NONE,
            THRESHOLD_MILLIS,
        )
        assertEquals(StallDetectionPolicy.Health.HEALTHY, result.health)
        assertEquals(false, result.inGracePeriod)
    }
}
