package com.uacastplayer.ui.player
import com.uacastplayer.ui.theme.UaTheme

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.uacastplayer.R
import com.uacastplayer.cast.CodecDisplayName
import com.uacastplayer.cast.CodecIncompatibility
import com.uacastplayer.core.ui.findActivity
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.player.AudioChannelLayout
import com.uacastplayer.player.PlaybackBadgesState
import com.uacastplayer.player.PlayerGesturePolicy
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.player.ResizeModeCycle
import com.uacastplayer.player.StallRetryPolicy
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.cast.CastButton
import com.uacastplayer.ui.dlna.DlnaDeviceSheet
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.raisedSurface
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.IconButtonSize
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Title
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.delay

private const val CONTROLS_AUTO_HIDE_MILLIS = 3000L
private const val GESTURE_INDICATOR_AUTO_HIDE_MILLIS = 900L

@OptIn(markerClass = [UnstableApi::class])
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onExit: () -> Unit,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    resolveIcon: suspend (M3uChannel) -> File?,
    epgState: EpgUiState,
    iconPrefetchState: IconPrefetchUiState,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // Same idea as ChannelsScreen's iconRefreshKey - forces the channel icons on this screen to
    // re-resolve once EPG data arrives or a prefetch run finishes writing new files.
    val iconRefreshKey: Any = (epgState.data != null) to iconPrefetchState.completedRuns
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val haptics = LocalHapticFeedback.current
    val dlnaState by viewModel.dlnaState.collectAsStateWithLifecycle()

    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showGuideSheet by remember { mutableStateOf(false) }
    var showDlnaSheet by remember { mutableStateOf(false) }
    val videoResizeMode = ResizeModeCycle.toMedia3ResizeMode(uiState.resizeMode)
    val sleepTimer = rememberSleepTimerState(onExpire = { viewModel.player.pause() })

    // Tracks the app's own brightness/volume overrides across drags - re-reading the system value
    // every frame would fight the WindowManager override this same gesture just applied.
    var brightnessLevel by remember(activity) {
        mutableStateOf(activity?.let(::initialBrightnessLevel) ?: DEFAULT_BRIGHTNESS_LEVEL)
    }
    var volumeLevel by remember(audioManager) { mutableStateOf(audioManager.currentVolumeFraction()) }
    var gestureIndicator by remember { mutableStateOf<GestureIndicatorKind?>(null) }
    var gestureIndicatorNonce by remember { mutableStateOf(0) }
    var resizeModeToastNonce by remember { mutableStateOf(0) }
    var showResizeModeToast by remember { mutableStateOf(false) }

    LaunchedEffect(gestureIndicatorNonce) {
        if (gestureIndicator != null) {
            delay(GESTURE_INDICATOR_AUTO_HIDE_MILLIS)
            gestureIndicator = null
        }
    }

    LaunchedEffect(resizeModeToastNonce) {
        if (resizeModeToastNonce > 0) {
            showResizeModeToast = true
            delay(GESTURE_INDICATOR_AUTO_HIDE_MILLIS)
            showResizeModeToast = false
        }
    }

    val configuration = LocalConfiguration.current
    val isInPip = remember(configuration, activity) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity?.isInPictureInPictureMode == true
    }

    // Where the video surface currently sits on screen, for PiP's source-rect hint (see below).
    var videoBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

    // From API 31 the system can enter PiP by itself when the user gestures home, but only if the
    // activity's params already say so - by the time onUserLeaveHint would fire it is too late.
    // Keeping them in sync here is what makes swiping home during playback continue the channel in
    // a floating window instead of just backgrounding the app. Gated on actually playing, and on
    // fullscreen: auto-entering PiP from a paused, errored or cast-only player would put an empty
    // window on screen. The same params carry the real aspect ratio, so the window is the shape of
    // the channel rather than an assumed 16:9.
    val autoEnterPip = isFullscreen && uiState.isPlaying && !uiState.isCasting && !uiState.fatalError
    LaunchedEffect(activity, autoEnterPip, videoBounds, uiState.videoSize) {
        activity?.let { PipController.syncParams(it, uiState.videoSize, videoBounds, autoEnterPip) }
    }

    DisposableEffect(activity, isFullscreen) {
        activity?.let { FullscreenController.apply(it, isFullscreen) }
        onDispose { activity?.let { FullscreenController.apply(it, enabled = false) } }
    }

    // The brightness drag gesture below overrides the window's brightness directly - without this,
    // leaving the player (including via PiP teardown, which also disposes this composable) would
    // permanently pin the screen at whatever level the gesture last set, even in other apps.
    DisposableEffect(activity) {
        onDispose { activity?.let(::restoreWindowBrightness) }
    }

    // Keep the screen on only while this device is actually rendering the stream - while casting,
    // the phone is idle (see LocalPlaybackPolicy) and letting it sleep is the expected behavior.
    // Always reset on dispose so leaving the player never leaves the flag stuck on.
    val view = LocalView.current
    DisposableEffect(view, uiState.isPlaying, uiState.isCasting) {
        view.keepScreenOn = uiState.isPlaying && !uiState.isCasting
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(controlsVisible, uiState.isPlaying) {
        if (controlsVisible && uiState.isPlaying) {
            delay(CONTROLS_AUTO_HIDE_MILLIS)
            controlsVisible = false
        }
    }

    val currentChannel = uiState.currentChannel

    if (isInPip) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view -> if (view.player !== viewModel.player) view.player = viewModel.player },
            // See VideoSurface's own onRelease - a disposed PlayerView must not keep holding the
            // Player, or entering/leaving PiP races the surface with whichever PlayerView mounts next.
            onRelease = { it.player = null },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    if (isFullscreen) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                // See the inline branch's video Box below for why this is combinedClickable and not
                // a raw pointerInput(Unit) { detectTapGestures {...} } - it would race
                // PlayerControlsOverlay's own button clicks for the same down/up events.
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { controlsVisible = !controlsVisible },
                    onDoubleClick = {
                        if (viewModel.player.isPlaying) viewModel.player.pause() else viewModel.player.play()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )
                .pointerInput(activity, audioManager) {
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
                                PlayerGesturePolicy.SwipeChannelAction.NEXT -> viewModel.requestNext()
                                PlayerGesturePolicy.SwipeChannelAction.PREVIOUS -> viewModel.requestPrevious()
                                null -> Unit
                            }
                            if (action != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                                brightnessLevel = PlayerGesturePolicy.applyLevelDelta(brightnessLevel, delta)
                                activity?.let { applyWindowBrightness(it, brightnessLevel) }
                                gestureIndicator = GestureIndicatorKind.BRIGHTNESS
                                gestureIndicatorNonce++
                                change.consume()
                            }
                            PlayerGesturePolicy.GestureZone.RIGHT -> {
                                volumeLevel = PlayerGesturePolicy.applyLevelDelta(volumeLevel, delta)
                                audioManager?.let { applyStreamVolume(it, volumeLevel) }
                                gestureIndicator = GestureIndicatorKind.VOLUME
                                gestureIndicatorNonce++
                                change.consume()
                            }
                            PlayerGesturePolicy.GestureZone.CENTER -> Unit
                        }
                    }
                },
        ) {
            VideoSurface(
                viewModel = viewModel,
                resizeMode = videoResizeMode,
                // Reported so PiP can hand the system a source rectangle to scale out of, instead
                // of cross-fading from nowhere. These are the surface's bounds, not the picture's:
                // a letterboxed stream leaves bars inside them, which is the usual approximation
                // and costs a slightly larger starting rect, nothing more.
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { videoBounds = it.boundsInWindow().toAndroidRect() },
            )

            if (uiState.isRecoveringPlayback) {
                RecoveringPlaybackIndicator(
                    attempt = uiState.stallRecoveryAttempt,
                    onPickAnotherChannel = onExit,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else if (uiState.isBuffering && !uiState.fatalError) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            if (uiState.fatalError) {
                Text(
                    text = stringResource(R.string.player_fatal_error),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }

            gestureIndicator?.let { kind ->
                GestureLevelIndicator(
                    kind = kind,
                    level = if (kind == GestureIndicatorKind.BRIGHTNESS) brightnessLevel else volumeLevel,
                    modifier = Modifier
                        .align(if (kind == GestureIndicatorKind.BRIGHTNESS) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = GapL),
                )
            }

            if (controlsVisible) {
                PlayerControlsOverlay(
                    uiState = uiState,
                    isFullscreen = isFullscreen,
                    sleepTimerRemainingMillis = sleepTimer.remainingMillis,
                    brightnessLevel = brightnessLevel,
                    volumeLevel = volumeLevel,
                    onExit = onExit,
                    onPlayPause = {
                        if (viewModel.player.isPlaying) viewModel.player.pause() else viewModel.player.play()
                    },
                    onNext = viewModel::requestNext,
                    onPrevious = viewModel::requestPrevious,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onEnterPip = { activity?.let { PipController.enter(it, uiState.videoSize, videoBounds) } },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    isDlnaCasting = dlnaState.connectedDevice != null,
                    onOpenDlnaSheet = { showDlnaSheet = true },
                    onSelectPreview = { indexed -> viewModel.requestSwitch(indexed.index) },
                    // Same effect as the drag gesture above, just reachable from TalkBack - one
                    // fixed step per tap instead of a continuous drag delta.
                    onBrightnessStep = { delta ->
                        brightnessLevel = PlayerGesturePolicy.applyLevelDelta(brightnessLevel, delta)
                        activity?.let { applyWindowBrightness(it, brightnessLevel) }
                        gestureIndicator = GestureIndicatorKind.BRIGHTNESS
                        gestureIndicatorNonce++
                    },
                    onVolumeStep = { delta ->
                        volumeLevel = PlayerGesturePolicy.applyLevelDelta(volumeLevel, delta)
                        audioManager?.let { applyStreamVolume(it, volumeLevel) }
                        gestureIndicator = GestureIndicatorKind.VOLUME
                        gestureIndicatorNonce++
                    },
                )
            }
        }
    } else {
        Column(
            // An explicit opaque background - since RootScaffold now stays mounted underneath the
            // fullscreen player rather than unmounting (see MainActivity's PlayerHost overlay
            // comment), any transparent gap here would let it bleed through visually.
            modifier = modifier
                .fillMaxSize()
                .background(UaTheme.palette.void)
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallRoundIconButton(icon = AppIcons.ArrowBack, onClick = onExit, contentDescription = stringResource(R.string.common_back))
                Text(
                    // The channel being watched, not the app's own name - which is what this said
                    // before, on the one screen where the user already knows which app they are in
                    // and the single most useful thing to show is what is playing. Falls back to
                    // the app name only while nothing is loaded yet.
                    text = currentChannel?.displayName ?: stringResource(R.string.app_name),
                    style = Title,
                    color = UaTheme.palette.labelPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (currentChannel != null) {
                    SmallRoundIconButton(
                        icon = AppIcons.Favorites,
                        onClick = { onToggleFavorite(currentChannel) },
                        contentDescription = stringResource(R.string.favorites_title),
                        tint = if (isFavorite(currentChannel)) UaTheme.palette.azure else UaTheme.palette.labelPrimary,
                    )
                }
                PlayerCastButton(background = UaTheme.palette.surface1, modifier = Modifier.padding(start = 8.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHPadding)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(RadiusCard))
                    .background(Color.Black)
                    // A raw pointerInput(Unit) { detectTapGestures {...} } here would race the
                    // InlineVideoControls buttons' own clickable() for the same down/up events -
                    // Compose's gesture-consumption arbitration between two independent detectors
                    // covering the same area is unreliable and could make a button silently eat a
                    // tap with neither callback firing. clickable() nests correctly with child
                    // clickables (innermost wins), so it doesn't have that race.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { controlsVisible = !controlsVisible },
                    ),
            ) {
                VideoSurface(viewModel = viewModel, resizeMode = videoResizeMode, modifier = Modifier.fillMaxSize())

                if (uiState.isRecoveringPlayback) {
                    RecoveringPlaybackIndicator(
                        attempt = uiState.stallRecoveryAttempt,
                        onPickAnotherChannel = onExit,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (uiState.isBuffering && !uiState.fatalError) {
                    Text(
                        text = stringResource(R.string.player_buffering),
                        color = Color.White,
                        style = Caption,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(UaTheme.palette.scrimBackground)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                if (uiState.fatalError) {
                    Text(
                        text = stringResource(R.string.player_fatal_error),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }
                if (showResizeModeToast) {
                    ResizeModeToast(mode = uiState.resizeMode, modifier = Modifier.align(Alignment.Center))
                }

                if (controlsVisible) {
                    InlineVideoControls(
                        isPlaying = uiState.isPlaying,
                        onPlayPause = {
                            if (viewModel.player.isPlaying) viewModel.player.pause() else viewModel.player.play()
                        },
                        onToggleFullscreen = { isFullscreen = true },
                    )
                }
            }

            // Codec incompatibility is the more specific, actionable explanation, so it takes
            // precedence in the rare case both could apply (see CastReceiverStatusReducer).
            // Recovering comes next - CastRecoveryPolicy is actively retrying, so there's no
            // failure to explain yet, just a transient hiccup. A LikelyCompatible hint is weaker
            // still - it only ever supplies a likely cause for a receiverLoadFailed that already
            // happened (recovery gave up), never a standalone reason on its own (video hint over
            // audio hint, matching CastCompatibilityPolicy's own priority).
            val incompatibility = uiState.castCodecIncompatibility
            val hint = uiState.castLikelyCompatibilityHint
            val castIncompatibilityMessage = when {
                incompatibility is CodecIncompatibility.Video ->
                    stringResource(R.string.cast_incompatible_video_message, CodecDisplayName.of(incompatibility.codec))
                uiState.castIsRecovering -> stringResource(R.string.cast_recovering_message)
                uiState.castProxyUnavailableIpv4Only -> stringResource(R.string.cast_proxy_ipv4_unavailable_message)
                uiState.castReceiverLoadFailed && hint?.videoHint != null ->
                    stringResource(R.string.cast_likely_incompatible_video_message, CodecDisplayName.of(hint.videoHint))
                uiState.castReceiverLoadFailed && hint?.audioHint != null ->
                    stringResource(R.string.cast_likely_incompatible_audio_message, CodecDisplayName.of(hint.audioHint))
                uiState.castReceiverLoadFailed -> stringResource(R.string.cast_receiver_load_failed_message)
                else -> null
            }
            castIncompatibilityMessage?.let { message ->
                CastIncompatibilityBanner(
                    message = message,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 4.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = GapM),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PillButton(
                    icon = AppIcons.SkipPrevious,
                    label = stringResource(R.string.player_previous),
                    onClick = viewModel::requestPrevious,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    icon = AppIcons.SkipNext,
                    label = stringResource(R.string.player_next),
                    onClick = viewModel::requestNext,
                    modifier = Modifier.weight(1f),
                    iconTrailing = true,
                )
            }

            if (currentChannel != null) {
                ChannelInfoCard(
                    channel = currentChannel,
                    badges = uiState.badges,
                    iconRefreshKey = iconRefreshKey,
                    resolveIcon = resolveIcon,
                )
            }

            QuickSettingsRow(
                onAudioClick = { showAudioDialog = true },
                onSubtitlesClick = { showSubtitleDialog = true },
                onQualityClick = { showQualityDialog = true },
                onAspectRatioClick = {
                    viewModel.cycleResizeMode()
                    resizeModeToastNonce++
                },
                onGuideClick = { showGuideSheet = true },
                onPreviousChannelClick = if (uiState.hasPreviousChannel) viewModel::requestPreviousChannel else null,
            )

            if (uiState.nextChannelsPreview.isNotEmpty()) {
                NextChannelsRail(
                    channels = uiState.nextChannelsPreview,
                    iconRefreshKey = iconRefreshKey,
                    resolveIcon = resolveIcon,
                    onSelect = { indexed -> viewModel.requestSwitch(indexed.index) },
                )
            }

            Button(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = GapL),
            ) {
                Text(stringResource(R.string.player_back_to_channels))
            }
        }
    }

    if (showDlnaSheet) {
        DlnaDeviceSheet(
            connectionState = dlnaState,
            discoverDevices = viewModel::discoverDlnaDevices,
            onDismiss = { showDlnaSheet = false },
            // The sheet stays open on connect: connecting is asynchronous and the row it lists
            // turns into the connected row, which is also where "stop casting" lives. Closing here
            // would hide the only feedback that the tap did anything.
            onDeviceSelected = viewModel::connectDlna,
            onStopCasting = viewModel::stopDlna,
        )
    }

    PlayerDialogs(
        viewModel = viewModel,
        uiState = uiState,
        epgState = epgState,
        sleepTimer = sleepTimer,
        currentChannel = currentChannel,
        showSleepTimerDialog = showSleepTimerDialog,
        onDismissSleepTimerDialog = { showSleepTimerDialog = false },
        showAudioDialog = showAudioDialog,
        onDismissAudioDialog = { showAudioDialog = false },
        showSubtitleDialog = showSubtitleDialog,
        onDismissSubtitleDialog = { showSubtitleDialog = false },
        showQualityDialog = showQualityDialog,
        onDismissQualityDialog = { showQualityDialog = false },
        showGuideSheet = showGuideSheet,
        onDismissGuideSheet = { showGuideSheet = false },
    )
}

/**
 * Shown in place of the plain buffering spinner while [StallRetryPolicy] is actively retrying a
 * silent stall (see [PlayerViewModel.performStallRecovery]) - the automatic retries never stop on
 * their own, so this exists purely to reassure the user the app is still working on it instead of
 * leaving a frozen frame with no explanation. Past [StallRetryPolicy.CHANNEL_PICKER_HINT_ATTEMPT]
 * failed attempts, [onPickAnotherChannel] becomes visible as an escape hatch - the automatic
 * retries keep going in the background regardless of whether it's tapped.
 */
@Composable
private fun RecoveringPlaybackIndicator(attempt: Int, onPickAnotherChannel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusCard))
            .background(UaTheme.palette.scrimBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
            Text(
                text = stringResource(R.string.player_recovering_playback),
                color = Color.White,
                style = Caption,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        if (attempt >= StallRetryPolicy.CHANNEL_PICKER_HINT_ATTEMPT) {
            Text(
                text = stringResource(R.string.player_pick_another_channel),
                color = UaTheme.palette.azure,
                style = Caption,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable(onClick = onPickAnotherChannel),
            )
        }
    }
}

/**
 * [CastButton] wrapped to match the size/shape of the other circular overlay icons (see
 * [SmallRoundIconButton]) - the platform MediaRouteButton has its own default sizing/padding that
 * doesn't line up with the app's icon buttons otherwise.
 */
@Composable
internal fun PlayerCastButton(modifier: Modifier = Modifier, background: Color = UaTheme.palette.scrimBackground) {
    Box(
        // Same raisedSurface treatment as SmallRoundIconButton (the other circular overlay icons
        // in this row) instead of a flat background - without it this button had no edge highlight
        // or gradient lift, so it visually read as a separate, out-of-place element even though it
        // was already the same size and position.
        modifier = modifier
            .size(IconButtonSize)
            .raisedSurface(CircleShape, background, shadow = false),
        contentAlignment = Alignment.Center,
    ) {
        CastButton(modifier = Modifier.size(IconButtonSize))
    }
}

@Composable
internal fun playPauseLabel(isPlaying: Boolean) =
    stringResource(if (isPlaying) R.string.player_pause else R.string.player_play)

@Composable
internal fun BadgesRow(badges: PlaybackBadgesState, modifier: Modifier = Modifier) {
    val parts = buildList {
        badges.qualityLabel?.let(::add)
        badges.videoCodecLabel?.let(::add)
        badges.audioCodecLabel?.let(::add)
        badges.channelLayout?.let { add(stringResource(it.labelRes())) }
    }
    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString(" · "),
            color = UaTheme.palette.labelSecondary,
            style = com.uacastplayer.ui.theme.Caption,
            modifier = modifier,
        )
    }
}

internal fun AudioChannelLayout.labelRes(): Int = when (this) {
    AudioChannelLayout.MONO -> R.string.audio_layout_mono
    AudioChannelLayout.STEREO -> R.string.audio_layout_stereo
    AudioChannelLayout.SURROUND_5_1 -> R.string.audio_layout_surround_5_1
    AudioChannelLayout.SURROUND_7_1 -> R.string.audio_layout_surround_7_1
    AudioChannelLayout.OTHER -> R.string.audio_layout_stereo
}
