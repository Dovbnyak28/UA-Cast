package com.uacastplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.player.PlayerSleepTimer
import kotlin.time.Duration

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
 * Observes the playback-owned timer. Disposing this screen must not cancel its deadline when the
 * same player moves into mini-player mode or continues across a configuration change.
 */
@Composable
internal fun rememberSleepTimerState(timer: PlayerSleepTimer): SleepTimerState {
    val remaining = timer.remainingMillis.collectAsStateWithLifecycle()
    return remember(timer, remaining) { SleepTimerState(remaining, timer::start, timer::cancel) }
}
