package com.uacastplayer.player

import com.uacastplayer.core.settings.BufferSize

/**
 * Detects a "silent" stall - a live stream that stopped delivering bytes without ever breaking the
 * connection, so ExoPlayer never raises a [androidx.media3.common.PlaybackException] and just sits
 * either buffering forever or reporting READY with a position that has stopped advancing. Neither
 * state alone is a stall (buffering routinely happens briefly; a paused player never advances by
 * design) - only a live, user-intends-to-play stream stuck in one of those states for longer than
 * its [BufferSize] tier's threshold counts.
 *
 * Callers drive this incrementally: each ~2s tick calls [evaluate] with the current state and the
 * [StallState] returned by the previous call (start with [StallState.NONE]), so the caller doesn't
 * need to track its own timers - the elapsed-stall duration lives entirely in the returned state.
 */
object StallDetectionPolicy {

    enum class Health { HEALTHY, STALLED }

    /** Mirrors the handful of [androidx.media3.common.Player] states this policy cares about -
     * kept as a local enum so this file has no Media3/Android dependency. */
    enum class PlaybackPhase { BUFFERING, READY, OTHER }

    data class Tick(
        val nowMillis: Long,
        val positionMs: Long,
        val phase: PlaybackPhase,
        val playWhenReady: Boolean,
        val isLive: Boolean,
    )

    /** [stallStartMillis] is null while healthy; once a stalling streak begins it's pinned to the
     * tick that started it, so elapsed duration is always `tick.nowMillis - stallStartMillis`
     * regardless of how many ticks have happened in between. [recoveryGraceUntilMillis] is set by
     * [afterRecovery] right when a recovery attempt is scheduled: a stop/prepare/play or
     * seekToDefaultPosition recovery is itself guaranteed to re-buffer for a moment, which - without
     * this - [evaluate] would immediately count as a brand new stall, defeating the recovery before
     * it even has a chance to work (this was the actual cause of "playback dies a few minutes in" -
     * see StallRetryPolicy's KDoc for the full failure chain this grace period breaks). */
    data class StallState(
        val stallStartMillis: Long?,
        val previousPositionMs: Long?,
        val recoveryGraceUntilMillis: Long? = null,
    ) {
        companion object {
            val NONE = StallState(stallStartMillis = null, previousPositionMs = null, recoveryGraceUntilMillis = null)
        }
    }

    /** [inGracePeriod] is true when this tick was reported healthy only because a recovery grace
     * period (see [StallState.recoveryGraceUntilMillis]) is still active, not because playback was
     * actually confirmed advancing - callers that show a "recovering" indicator should keep it up
     * while this is true and only clear it once a genuinely healthy (non-grace) tick arrives. */
    data class Result(val health: Health, val state: StallState, val inGracePeriod: Boolean = false)

    private const val SMALL_THRESHOLD_MILLIS = 8_000L
    private const val MEDIUM_THRESHOLD_MILLIS = 12_000L
    private const val LARGE_THRESHOLD_MILLIS = 20_000L
    private const val MIN_RECOVERY_GRACE_MILLIS = 20_000L
    private const val RECOVERY_GRACE_THRESHOLD_MULTIPLIER = 2

    fun thresholdMillisFor(bufferSize: BufferSize): Long = when (bufferSize) {
        BufferSize.SMALL -> SMALL_THRESHOLD_MILLIS
        BufferSize.MEDIUM -> MEDIUM_THRESHOLD_MILLIS
        BufferSize.LARGE -> LARGE_THRESHOLD_MILLIS
    }

    /** Call the moment a recovery attempt is scheduled/performed, so the next ticks - which will
     * see the re-buffering that recovery itself causes - don't immediately reopen a new stall
     * streak. Grace lasts `max(2x the stall threshold, 20s)`: long enough to cover a recovery on a
     * slow connection, short enough that a recovery that genuinely didn't work is still caught. */
    fun afterRecovery(nowMillis: Long, thresholdMillis: Long): StallState {
        val graceMillis = maxOf(RECOVERY_GRACE_THRESHOLD_MULTIPLIER * thresholdMillis, MIN_RECOVERY_GRACE_MILLIS)
        return StallState(
            stallStartMillis = null,
            previousPositionMs = null,
            recoveryGraceUntilMillis = nowMillis + graceMillis,
        )
    }

    fun evaluate(tick: Tick, previous: StallState, thresholdMillis: Long): Result {
        val graceUntil = previous.recoveryGraceUntilMillis
        if (graceUntil != null && tick.nowMillis < graceUntil) return duringGrace(tick, graceUntil)
        return evaluateStalling(tick, previous, thresholdMillis)
    }

    private fun evaluateStalling(tick: Tick, previous: StallState, thresholdMillis: Long): Result {
        val isStalling = tick.isLive && tick.playWhenReady && (
            tick.phase == PlaybackPhase.BUFFERING ||
                (
                    tick.phase == PlaybackPhase.READY &&
                        previous.previousPositionMs != null &&
                        tick.positionMs <= previous.previousPositionMs
                    )
            )
        if (!isStalling) {
            val nextPosition = StallState(stallStartMillis = null, previousPositionMs = tick.positionMs)
            return Result(Health.HEALTHY, nextPosition)
        }

        val stallStartMillis = previous.stallStartMillis ?: tick.nowMillis
        val elapsed = tick.nowMillis - stallStartMillis
        val health = if (elapsed >= thresholdMillis) Health.STALLED else Health.HEALTHY
        val nextState = StallState(stallStartMillis = stallStartMillis, previousPositionMs = tick.positionMs)
        return Result(health, nextState)
    }

    private fun duringGrace(tick: Tick, graceUntil: Long): Result {
        val next = StallState(
            stallStartMillis = null,
            previousPositionMs = tick.positionMs,
            recoveryGraceUntilMillis = graceUntil,
        )
        return Result(Health.HEALTHY, next, inGracePeriod = true)
    }
}
