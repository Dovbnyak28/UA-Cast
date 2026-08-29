@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.uacastplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.uacastplayer.player.PlayerGesturePolicy
import com.uacastplayer.ui.theme.GapL
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private fun Rect.toEnclosingRect(): android.graphics.Rect = android.graphics.Rect(
    floor(left).toInt(),
    floor(top).toInt(),
    ceil(right).toInt(),
    ceil(bottom).toInt(),
)

@Composable
internal fun FullscreenPlayerContent(
    content: PlayerScreenContent,
    actions: PlayerScreenActions,
    environment: PlayerScreenEnvironment,
    transientState: PlayerScreenTransientState,
    sleepTimer: SleepTimerState,
    modifier: Modifier = Modifier,
) {
    val uiState = content.uiState
    val viewModel = actions.viewModel
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { transientState.controlsVisible = !transientState.controlsVisible },
                onDoubleClick = {
                    if (viewModel.player.isPlaying) viewModel.player.pause() else viewModel.player.play()
                    environment.haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            )
            .fullscreenGestureInput(actions, environment, transientState),
    ) {
        VideoSurface(
            viewModel = viewModel,
            resizeMode = content.videoResizeMode,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    transientState.videoBounds = it.boundsInWindow().toEnclosingRect()
                },
        )
        FullscreenPlaybackStatus(uiState, actions, modifier = Modifier.align(Alignment.Center))
        FullscreenGestureIndicator(transientState)
        if (!uiState.fatalError) {
            FullscreenControls(content, actions, environment, transientState, sleepTimer)
        }
    }
}

private fun Modifier.fullscreenGestureInput(
    actions: PlayerScreenActions,
    environment: PlayerScreenEnvironment,
    transientState: PlayerScreenTransientState,
): Modifier = pointerInput(environment.activity, environment.audioManager) {
    var zone = PlayerGesturePolicy.GestureZone.CENTER
    var horizontalTravel = 0f
    detectDragGestures(
        onDragStart = { offset ->
            zone = PlayerGesturePolicy.zoneFor(offset.x / size.width.toFloat())
            horizontalTravel = 0f
        },
        onDragEnd = {
            val action = PlayerGesturePolicy.channelSwipeAction(horizontalTravel / size.width.toFloat())
            when (action) {
                PlayerGesturePolicy.SwipeChannelAction.NEXT -> actions.viewModel.navigation.requestNext()
                PlayerGesturePolicy.SwipeChannelAction.PREVIOUS -> actions.viewModel.navigation.requestPrevious()
                null -> Unit
            }
            if (action != null) {
                environment.haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        },
    ) { change, dragAmount ->
        if (abs(dragAmount.x) > abs(dragAmount.y)) {
            horizontalTravel += dragAmount.x
            change.consume()
            return@detectDragGestures
        }
        val delta = PlayerGesturePolicy.levelDelta(dragAmount.y / size.height.toFloat())
        when (zone) {
            PlayerGesturePolicy.GestureZone.LEFT -> {
                transientState.brightnessLevel = PlayerGesturePolicy.applyLevelDelta(
                    transientState.brightnessLevel,
                    delta,
                )
                environment.activity?.let { applyWindowBrightness(it, transientState.brightnessLevel) }
                transientState.showGesture(GestureIndicatorKind.BRIGHTNESS)
                change.consume()
            }
            PlayerGesturePolicy.GestureZone.RIGHT -> {
                transientState.volumeLevel = PlayerGesturePolicy.applyLevelDelta(
                    transientState.volumeLevel,
                    delta,
                )
                environment.audioManager?.let { applyStreamVolume(it, transientState.volumeLevel) }
                transientState.showGesture(GestureIndicatorKind.VOLUME)
                change.consume()
            }
            PlayerGesturePolicy.GestureZone.CENTER -> Unit
        }
    }
}

@Composable
private fun BoxScope.FullscreenGestureIndicator(transientState: PlayerScreenTransientState) {
    transientState.gestureIndicator?.let { kind ->
        val isBrightness = kind == GestureIndicatorKind.BRIGHTNESS
        GestureLevelIndicator(
            kind = kind,
            level = if (isBrightness) transientState.brightnessLevel else transientState.volumeLevel,
            modifier = Modifier
                .align(if (isBrightness) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = GapL),
        )
    }
}

@Composable
private fun FullscreenControls(
    content: PlayerScreenContent,
    actions: PlayerScreenActions,
    environment: PlayerScreenEnvironment,
    transientState: PlayerScreenTransientState,
    sleepTimer: SleepTimerState,
) {
    if (!transientState.controlsVisible) return
    val uiState = content.uiState
    val viewModel = actions.viewModel
    PlayerControlsOverlay(
        uiState = uiState,
        isFullscreen = true,
        sleepTimerRemainingMillis = sleepTimer.remainingMillis,
        brightnessLevel = transientState.brightnessLevel,
        volumeLevel = transientState.volumeLevel,
        onExit = actions.onExit,
        onPlayPause = {
            if (viewModel.player.isPlaying) viewModel.player.pause() else viewModel.player.play()
        },
        onNext = viewModel.navigation::requestNext,
        onPrevious = viewModel.navigation::requestPrevious,
        onToggleFullscreen = { actions.onFullscreenChanged(false) },
        onEnterPip = {
            environment.activity?.let {
                PipController.enter(it, uiState.videoSize, transientState.videoBounds)
            }
        },
        onOpenSleepTimer = { transientState.showSleepTimerDialog = true },
        isDlnaCasting = content.dlnaState.connectedDevice != null,
        onOpenDlnaSheet = { transientState.showDlnaSheet = true },
        onSelectPreview = { viewModel.navigation.requestSwitch(it.index) },
        onBrightnessStep = { delta ->
            transientState.brightnessLevel = PlayerGesturePolicy.applyLevelDelta(
                transientState.brightnessLevel,
                delta,
            )
            environment.activity?.let { applyWindowBrightness(it, transientState.brightnessLevel) }
            transientState.showGesture(GestureIndicatorKind.BRIGHTNESS)
        },
        onVolumeStep = { delta ->
            transientState.volumeLevel = PlayerGesturePolicy.applyLevelDelta(
                transientState.volumeLevel,
                delta,
            )
            environment.audioManager?.let { applyStreamVolume(it, transientState.volumeLevel) }
            transientState.showGesture(GestureIndicatorKind.VOLUME)
        },
    )
}

@Composable
private fun FullscreenPlaybackStatus(
    uiState: com.uacastplayer.player.PlayerUiState,
    actions: PlayerScreenActions,
    modifier: Modifier = Modifier,
) {
    if (uiState.autoSkipRecovery != null) {
        AutoSkipRecoveryIndicator(
            state = uiState.autoSkipRecovery,
            onCancel = actions.viewModel::cancelAutoSkipRecovery,
            modifier = modifier,
        )
    } else if (uiState.isRecoveringPlayback) {
        RecoveringPlaybackIndicator(
            attempt = uiState.stallRecoveryAttempt,
            onPickAnotherChannel = actions.onExit,
            modifier = modifier,
        )
    } else if (uiState.isBuffering && !uiState.fatalError) {
        CircularProgressIndicator(modifier = modifier)
    }
    if (uiState.fatalError) {
        PlaybackFailureCard(
            onRetry = actions.viewModel::retryCurrentChannel,
            onNext = actions.viewModel.navigation::requestNext,
            onExit = actions.onExit,
            modifier = modifier.padding(24.dp),
        )
    }
}
