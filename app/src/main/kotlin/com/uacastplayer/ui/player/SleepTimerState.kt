package com.uacastplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.uacastplayer.player.SleepTimerCalculator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/** User-facing snapshot + actions for the player's sleep timer. [remainingMillis] is exposed as a
 * [State] rather than a plain `Long?` so a caller can hand it down to whichever leaf composable
 * actually renders the countdown (see PlayerControlsOverlay's SleepTimerButton) without reading
 * `.value` itself - reading it here, in the same big composable that also owns fullscreen/gesture/
 * playback state, would recompose the *entire* player screen once a second while the timer runs. */
internal class SleepTimerState(
    val remainingMillis: State<Long?>,
    val start: (Duration) -> Unit,
    val cancel: () -> Unit,
)

/**
 * Counts down to a wall-clock end time (kept in `rememberSaveable` so it survives configuration
 * changes) and invokes [onExpire] once when it reaches zero. Not keyed on the current channel: a
 * timer set on one channel keeps counting down across channel switches, same as a physical TV's
 * sleep timer.
 */
@Composable
internal fun rememberSleepTimerState(onExpire: () -> Unit): SleepTimerState {
    var endTimeMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val remainingMillisState = remember { mutableStateOf<Long?>(null) }
    val latestOnExpire by rememberUpdatedState(onExpire)

    LaunchedEffect(endTimeMillis) {
        val end = endTimeMillis
        if (end == null) {
            remainingMillisState.value = null
            return@LaunchedEffect
        }
        while (true) {
            val now = System.currentTimeMillis()
            if (SleepTimerCalculator.hasExpired(now, end)) {
                remainingMillisState.value = null
                endTimeMillis = null
                latestOnExpire()
                break
            }
            remainingMillisState.value = SleepTimerCalculator.remainingMillis(now, end)
            delay(1.seconds)
        }
    }

    return SleepTimerState(
        remainingMillis = remainingMillisState,
        start = { duration ->
            endTimeMillis = SleepTimerCalculator.endTimeMillis(System.currentTimeMillis(), duration)
        },
        cancel = { endTimeMillis = null },
    )
}
