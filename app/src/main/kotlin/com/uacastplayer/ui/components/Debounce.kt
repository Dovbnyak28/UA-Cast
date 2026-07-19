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
        delay(delayMillis)
        debounced = value
    }
    return debounced
}
