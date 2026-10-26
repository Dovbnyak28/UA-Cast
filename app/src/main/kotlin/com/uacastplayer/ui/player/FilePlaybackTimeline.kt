@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.uacastplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

internal data class FilePlaybackTimeline(val positionMs: Long = 0, val durationMs: Long = 0) {
    val duration: Long get() = durationMs.coerceAtLeast(0)
    val position: Long get() = positionMs.coerceIn(0, duration)
    val fraction: Float get() = if (duration > 0) (position.toDouble() / duration).toFloat() else 0f
    fun positionAt(fraction: Float): Long = if (fraction.isFinite()) {
        (fraction.coerceIn(0f, 1f).toDouble() * duration).toLong().coerceIn(0, duration)
    } else position
}

internal fun filePlaybackTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / MILLIS_PER_SECOND
    return if (seconds >= SECONDS_PER_HOUR) {
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / SECONDS_PER_HOUR,
            seconds / SECONDS_PER_MINUTE % SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
    } else {
        String.format(Locale.ROOT, "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
    }
}

/** A visible UI projection, not another playback owner. Paused files are event-driven, not polled. */
@Composable
internal fun rememberFilePlaybackTimeline(
    player: Player, mediaKey: Any?, playing: Boolean,
): State<FilePlaybackTimeline> {
    val owner = LocalLifecycleOwner.current
    val timeline = remember(player, mediaKey) { mutableStateOf(FilePlaybackTimeline()) }
    LifecycleStartEffect(player, mediaKey, lifecycleOwner = owner) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { timeline.value = readFileTimeline(player) }
        }
        player.addListener(listener)
        timeline.value = readFileTimeline(player)
        onStopOrDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player, mediaKey, playing, owner) {
        if (playing) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                timeline.value = readFileTimeline(player)
                delay(FILE_PROGRESS_INTERVAL_MILLIS)
            }
        }
    }
    return timeline
}

private fun readFileTimeline(player: Player) = FilePlaybackTimeline(player.currentPosition, player.duration)
private const val FILE_PROGRESS_INTERVAL_MILLIS = 500L
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
