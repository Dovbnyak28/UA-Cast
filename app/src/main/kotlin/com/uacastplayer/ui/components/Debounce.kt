package com.uacastplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val DEFAULT_DEBOUNCE_MILLIS = 200L

/**
 * Returns [value], but only after it has stopped changing for [delayMillis] - each new [value]
 * restarts the wait instead of queuing it, so a fast typist only pays the downstream cost (e.g.
 * re-filtering a whole playlist) once they pause, not on every keystroke.
 */
@Composable
fun <T> rememberDebounced(value: T, delayMillis: Long = DEFAULT_DEBOUNCE_MILLIS): T {
    var debounced by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        // Nothing to wait out when the caller is back at the value already being reported - this is
        // what a *reset* looks like (clearing the field, or SingleGroupChannelList's per-group
        // rememberSaveable key snapping the query back to ""), and delaying it left the previous
        // group's filtered results on screen for the debounce window after switching groups.
        if (value == debounced) return@LaunchedEffect
        delay(delayMillis)
        debounced = value
    }
    return debounced
}
