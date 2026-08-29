package com.uacastplayer.player

import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private const val QUARTER_HOUR_MINUTES = 15
private const val HALF_HOUR_MINUTES = 30
private const val THREE_QUARTER_HOUR_MINUTES = 45
private const val ONE_HOUR_MINUTES = 60
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60

/** Preset durations offered in the player's sleep timer dialog. */
enum class SleepTimerDuration(val minutes: Int) {
    MIN_15(QUARTER_HOUR_MINUTES),
    MIN_30(HALF_HOUR_MINUTES),
    MIN_45(THREE_QUARTER_HOUR_MINUTES),
    MIN_60(ONE_HOUR_MINUTES);

    val duration: Duration get() = minutes.minutes
}

/** Pure wall-clock math for the sleep timer's countdown, kept separate from the Compose ticker. */
object SleepTimerCalculator {
    fun endTimeMillis(nowMillis: Long, duration: Duration): Long =
        nowMillis + duration.inWholeMilliseconds

    fun remainingMillis(nowMillis: Long, endTimeMillis: Long): Long =
        (endTimeMillis - nowMillis).coerceAtLeast(0L)

    fun hasExpired(nowMillis: Long, endTimeMillis: Long): Boolean = nowMillis >= endTimeMillis
}

/** Formats the sleep timer's remaining time for display on the player's action button. */
object SleepTimerFormatter {
    fun formatRemaining(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis / MILLIS_PER_SECOND).coerceAtLeast(0L)
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
