package com.uacastplayer.player

import com.uacastplayer.data.prefs.BufferSize

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
     * regardless of how many ticks have happened in between. */
    data class StallState(val stallStartMillis: Long?, val previousPositionMs: Long?) {
        companion object {
            val NONE = StallState(stallStartMillis = null, previousPositionMs = null)
        }
    }

    data class Result(val health: Health, val state: StallState)

    private const val SMALL_THRESHOLD_MILLIS = 8_000L
    private const val MEDIUM_THRESHOLD_MILLIS = 12_000L
    private const val LARGE_THRESHOLD_MILLIS = 20_000L

    fun thresholdMillisFor(bufferSize: BufferSize): Long = when (bufferSize) {
        BufferSize.SMALL -> SMALL_THRESHOLD_MILLIS
        BufferSize.MEDIUM -> MEDIUM_THRESHOLD_MILLIS
        BufferSize.LARGE -> LARGE_THRESHOLD_MILLIS
    }

    fun evaluate(tick: Tick, previous: StallState, thresholdMillis: Long): Result {
        val isStalling = tick.isLive && tick.playWhenReady && (
            tick.phase == PlaybackPhase.BUFFERING ||
                (
                    tick.phase == PlaybackPhase.READY &&
                        previous.previousPositionMs != null &&
                        tick.positionMs <= previous.previousPositionMs
                    )
            )
        val nextPosition = StallState(stallStartMillis = null, previousPositionMs = tick.positionMs)
        if (!isStalling) return Result(Health.HEALTHY, nextPosition)

        val stallStartMillis = previous.stallStartMillis ?: tick.nowMillis
        val elapsed = tick.nowMillis - stallStartMillis
        val health = if (elapsed >= thresholdMillis) Health.STALLED else Health.HEALTHY
        return Result(health, StallState(stallStartMillis = stallStartMillis, previousPositionMs = tick.positionMs))
    }
}
