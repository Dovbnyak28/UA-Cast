@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.uacastplayer.ui.player

import android.app.Activity
import android.graphics.Rect
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalView
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.playlist.M3uChannel
import java.io.File
import kotlinx.coroutines.delay

private const val CONTROLS_AUTO_HIDE_MILLIS = 3_000L
private const val GESTURE_INDICATOR_AUTO_HIDE_MILLIS = 900L

internal data class PlayerScreenContent(
    val uiState: PlayerUiState,
    val dlnaState: DlnaConnectionState,
    val iconRefreshKey: Any,
    val videoResizeMode: Int,
)

internal data class PlayerScreenActions(
    val viewModel: PlayerViewModel,
    val onExit: () -> Unit,
    val isFavorite: (M3uChannel) -> Boolean,
    val onToggleFavorite: (M3uChannel) -> Unit,
    val resolveIcon: suspend (M3uChannel) -> File?,
    val onFullscreenChanged: (Boolean) -> Unit,
)

internal data class PlayerScreenEnvironment(
    val activity: Activity?,
    val audioManager: AudioManager?,
    val haptics: HapticFeedback,
)

@Stable
internal class PlayerScreenTransientState(activity: Activity?, audioManager: AudioManager?) {
    var controlsVisible by mutableStateOf(true)
    var showSleepTimerDialog by mutableStateOf(false)
    var showAudioDialog by mutableStateOf(false)
    var showSubtitleDialog by mutableStateOf(false)
    var showQualityDialog by mutableStateOf(false)
    var showGuideSheet by mutableStateOf(false)
    var showDlnaSheet by mutableStateOf(false)
    var brightnessLevel by mutableFloatStateOf(
        activity?.let(::initialBrightnessLevel) ?: DEFAULT_BRIGHTNESS_LEVEL,
    )
    var volumeLevel by mutableFloatStateOf(audioManager.currentVolumeFraction())
    var gestureIndicator by mutableStateOf<GestureIndicatorKind?>(null)
    var gestureIndicatorNonce by mutableIntStateOf(0)
    var resizeModeToastNonce by mutableIntStateOf(0)
    var showResizeModeToast by mutableStateOf(false)
    var videoBounds by mutableStateOf<Rect?>(null)

    fun showGesture(kind: GestureIndicatorKind) {
        gestureIndicator = kind
        gestureIndicatorNonce++
    }
}

@Composable
internal fun PlayerScreenEffects(
    environment: PlayerScreenEnvironment,
    isFullscreen: Boolean,
    uiState: PlayerUiState,
    transientState: PlayerScreenTransientState,
) {
    LaunchedEffect(transientState.gestureIndicatorNonce) {
        if (transientState.gestureIndicator != null) {
            delay(GESTURE_INDICATOR_AUTO_HIDE_MILLIS)
            transientState.gestureIndicator = null
        }
    }
    LaunchedEffect(transientState.resizeModeToastNonce) {
        if (transientState.resizeModeToastNonce > 0) {
            transientState.showResizeModeToast = true
            delay(GESTURE_INDICATOR_AUTO_HIDE_MILLIS)
            transientState.showResizeModeToast = false
        }
    }

    val autoEnterPip = isFullscreen && uiState.isPlaying && !uiState.isCasting && !uiState.fatalError
    LaunchedEffect(environment.activity, autoEnterPip, transientState.videoBounds, uiState.videoSize) {
        environment.activity?.let {
            PipController.syncParams(it, uiState.videoSize, transientState.videoBounds, autoEnterPip)
        }
    }
    DisposableEffect(environment.activity, isFullscreen) {
        environment.activity?.let { FullscreenController.apply(it, isFullscreen) }
        onDispose {
            environment.activity?.let { FullscreenController.apply(it, enabled = false) }
        }
    }
    DisposableEffect(environment.activity) {
        onDispose { environment.activity?.let(::restoreWindowBrightness) }
    }

    val view = LocalView.current
    DisposableEffect(view, uiState.isPlaying, uiState.isCasting) {
        view.keepScreenOn = uiState.isPlaying && !uiState.isCasting
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(transientState.controlsVisible, uiState.isPlaying) {
        if (transientState.controlsVisible && uiState.isPlaying) {
            delay(CONTROLS_AUTO_HIDE_MILLIS)
            transientState.controlsVisible = false
        }
    }
}
