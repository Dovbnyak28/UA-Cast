package com.uacastplayer.player

import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Preset durations offered in the player's sleep timer dialog. */
enum class SleepTimerDuration(val minutes: Int) {
    MIN_15(15),
    MIN_30(30),
    MIN_45(45),
    MIN_60(60);

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
        val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
