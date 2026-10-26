package com.uacastplayer.ui.player

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

internal const val CONTROLS_AUTO_HIDE_MILLIS = 3_000L

/** Observe without consuming: child buttons and video gestures keep their own event handling. */
internal fun Modifier.playerControlsInteraction(state: PlayerScreenTransientState): Modifier =
    onFocusChanged { state.controlsFocused = it.hasFocus }.focusGroup().pointerInput(state) {
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    state.controlsPressed = event.changes.any { it.pressed }
                    state.controlsInteractionNonce++
                }
            }
        } finally {
            state.controlsPressed = false
        }
    }

@Composable
internal fun PlayerControlsAutoHide(state: PlayerScreenTransientState, isPlaying: Boolean) {
    val touchExploration = rememberTouchExploration()
    val timeout = (LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = CONTROLS_AUTO_HIDE_MILLIS,
        containsIcons = true,
        containsText = true,
        containsControls = true,
    ) ?: CONTROLS_AUTO_HIDE_MILLIS).coerceAtLeast(CONTROLS_AUTO_HIDE_MILLIS)
    val held = state.hasOpenSheet || state.controlsPressed || state.controlsFocused || touchExploration
    LaunchedEffect(state.controlsVisible, isPlaying, held, state.controlsInteractionNonce, timeout) {
        if (state.controlsVisible && isPlaying && !held) {
            delay(timeout)
            state.controlsVisible = false
        }
    }
}

@Composable
private fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        enabled = manager?.isTouchExplorationEnabled == true
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
