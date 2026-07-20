package com.uacastplayer.ui.player
import com.uacastplayer.ui.theme.UaTheme

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import com.uacastplayer.R
import com.uacastplayer.cast.CodecDisplayName
import com.uacastplayer.cast.CodecIncompatibility
import com.uacastplayer.core.ui.findActivity
import com.uacastplayer.data.prefs.PlayerResizeMode
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.player.AudioChannelLayout
import com.uacastplayer.player.BrightnessGestureStart
import com.uacastplayer.player.IndexedChannel
import com.uacastplayer.player.PlaybackBadgesState
import com.uacastplayer.player.PlayerGesturePolicy
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.player.ResizeModeCycle
import com.uacastplayer.player.SelectableTrack
import com.uacastplayer.player.SleepTimerFormatter
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.cast.CastButton
import com.uacastplayer.ui.components.GradientPlayButton
import com.uacastplayer.ui.components.RoundIconButton
import com.uacastplayer.ui.components.SleepTimerDialog
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.epg.EpgGuideSheet
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BreatheMs
import com.uacastplayer.ui.theme.CardPadding
import com.uacastplayer.ui.theme.DisplayName
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.IconButtonSize
import com.uacastplayer.ui.theme.LiveText
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.RadiusField
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.raisedSurface
import com.uacastplayer.ui.theme.Title
import androidx.compose.material3.Button
import com.uacastplayer.ui.components.ChannelIcon
import com.uacastplayer.ui.components.initialsFor
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.delay

private const val CONTROLS_AUTO_HIDE_MILLIS = 3000L
private const val GESTURE_INDICATOR_AUTO_HIDE_MILLIS = 900L
private const val DEFAULT_BRIGHTNESS_LEVEL = 0.5f

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

    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showGuideSheet by remember { mutableStateOf(false) }
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
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = {
                            if (viewModel.player.isPlaying) viewModel.player.pause() else viewModel.player.play()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                    )
                }
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
            VideoSurface(viewModel = viewModel, resizeMode = videoResizeMode, modifier = Modifier.fillMaxSize())

            if (uiState.isBuffering && !uiState.fatalError) {
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
                    onExit = onExit,
                    onPlayPause = {
                        if (viewModel.player.isPlaying) viewModel.player.pause() else viewModel.player.play()
                    },
                    onNext = viewModel::requestNext,
                    onPrevious = viewModel::requestPrevious,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onEnterPip = { activity?.let(PipController::enter) },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onSelectPreview = { indexed -> viewModel.requestSwitch(indexed.index) },
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
                    text = stringResource(R.string.app_name),
                    style = Title,
                    color = UaTheme.palette.labelPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                    .pointerInput(Unit) { detectTapGestures { controlsVisible = !controlsVisible } },
            ) {
                VideoSurface(viewModel = viewModel, resizeMode = videoResizeMode, modifier = Modifier.fillMaxSize())

                if (uiState.isBuffering && !uiState.fatalError) {
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

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            isTimerActive = sleepTimer.remainingMillis != null,
            onSelect = { duration ->
                sleepTimer.start(duration)
                showSleepTimerDialog = false
            },
            onCancelTimer = {
                sleepTimer.cancel()
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false },
        )
    }

    if (showAudioDialog) {
        TrackPickerDialog(
            title = stringResource(R.string.player_audio_track),
            tracks = uiState.audioTracks,
            onSelect = { viewModel.selectAudioTrack(it); showAudioDialog = false },
            onDismiss = { showAudioDialog = false },
        )
    }

    if (showSubtitleDialog) {
        TrackPickerDialog(
            title = stringResource(R.string.player_subtitle_track),
            tracks = uiState.textTracks,
            offLabel = stringResource(R.string.player_subtitle_off),
            onSelectOff = { viewModel.clearTextTrack(); showSubtitleDialog = false },
            onSelect = { viewModel.selectTextTrack(it); showSubtitleDialog = false },
            onDismiss = { showSubtitleDialog = false },
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text(stringResource(R.string.player_quality)) },
            text = { Text(uiState.badges.qualityLabel ?: stringResource(R.string.player_quality_unknown)) },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text(stringResource(R.string.common_back)) }
            },
        )
    }

    if (showGuideSheet && currentChannel != null) {
        EpgGuideSheet(
            channel = currentChannel,
            epgData = epgState.data,
            nowMillis = epgState.nowMillis,
            onDismiss = { showGuideSheet = false },
        )
    }
}

private enum class GestureIndicatorKind { BRIGHTNESS, VOLUME }

private fun AudioManager?.currentVolumeFraction(): Float {
    if (this == null) return DEFAULT_BRIGHTNESS_LEVEL
    val max = getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return DEFAULT_BRIGHTNESS_LEVEL
    return getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max.toFloat()
}

private fun applyWindowBrightness(activity: Activity, level: Float) {
    val window = activity.window
    val params = window.attributes
    params.screenBrightness = level.coerceIn(0.01f, 1f)
    window.attributes = params
}

/** Restores the window to following the system/auto brightness - undoes [applyWindowBrightness]. */
private fun restoreWindowBrightness(activity: Activity) {
    val window = activity.window
    val params = window.attributes
    params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    window.attributes = params
}

private const val MAX_SYSTEM_BRIGHTNESS = 255f

private fun initialBrightnessLevel(activity: Activity): Float {
    val systemBrightness = try {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / MAX_SYSTEM_BRIGHTNESS
    } catch (_: Settings.SettingNotFoundException) {
        null
    }
    return BrightnessGestureStart.level(activity.window.attributes.screenBrightness, systemBrightness)
}

private fun applyStreamVolume(audioManager: AudioManager, level: Float) {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val target = (level * max).toInt().coerceIn(0, max)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
}

/** Thin vertical fill bar shown while dragging in the brightness/volume zones of the fullscreen
 * player - mirrors the system overlay's shape but stays inside the app's own design system. */
@Composable
private fun GestureLevelIndicator(kind: GestureIndicatorKind, level: Float, modifier: Modifier = Modifier) {
    Column(
        // flat by design: deliberately mirrors the system volume/brightness overlay's minimal
        // look (see the KDoc above), not the app's own raised chrome.
        modifier = modifier
            .width(44.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(RadiusField))
            .background(UaTheme.palette.surface2)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (kind == GestureIndicatorKind.BRIGHTNESS) AppIcons.Brightness else AppIcons.Volume,
            contentDescription = null,
            tint = UaTheme.palette.azure,
            modifier = Modifier.size(18.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(RadiusField / 2))
                .background(UaTheme.palette.overlayHighlight),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(level.coerceIn(0f, 1f))
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(RadiusField / 2))
                    .background(UaTheme.palette.azure),
            )
        }
    }
}

/** Transient pill shown for [GESTURE_INDICATOR_AUTO_HIDE_MILLIS] after cycling the aspect ratio -
 * same visual language as the buffering pill, just centered instead of anchored to a corner since
 * it isn't tied to a screen edge the way the brightness/volume bars are. */
@Composable
private fun ResizeModeToast(mode: PlayerResizeMode, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(UaTheme.palette.scrimBackground)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Fullscreen, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(ResizeModeCycle.labelRes(mode)),
            color = Color.White,
            style = Caption,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** internal, not private - reused by [com.uacastplayer.ui.player.MiniPlayerBar], and called from
 * two different sites within this file (inline and fullscreen) - each call site is a distinct
 * composition node, so switching between them (e.g. collapsing fullscreen into the mini-bar)
 * disposes one `PlayerView` and creates another. */
@OptIn(markerClass = [UnstableApi::class])
@Composable
internal fun VideoSurface(viewModel: PlayerViewModel, resizeMode: Int, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = viewModel.player
                useController = false
            }
        },
        update = { view ->
            // update() can run on every recomposition of this call site even though the Player
            // instance hasn't changed - reassigning PlayerView.player unconditionally resets its
            // internal surface binding each time, which is what caused an occasional black frame
            // right after a fullscreen<->mini-bar transition (the newly composed PlayerView and the
            // about-to-be-disposed old one briefly both held the same live Player).
            if (view.player !== viewModel.player) view.player = viewModel.player
            view.resizeMode = resizeMode
        },
        // Without this, a disposed PlayerView keeps its `player` reference alive - the Player
        // itself thinks it still has a video output attached here even though this View is gone,
        // which is exactly the dangling-surface race the black-frame bug above comes from.
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

@Composable
private fun InlineVideoControls(isPlaying: Boolean, onPlayPause: () -> Unit, onToggleFullscreen: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        GradientPlayButton(
            icon = if (isPlaying) AppIcons.Pause else AppIcons.Play,
            onClick = onPlayPause,
            contentDescription = playPauseLabel(isPlaying),
            modifier = Modifier.align(Alignment.Center),
        )
        SmallRoundIconButton(
            icon = AppIcons.Fullscreen,
            onClick = onToggleFullscreen,
            contentDescription = stringResource(R.string.player_fullscreen),
            background = UaTheme.palette.scrimBackground,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        )
    }
}

@Composable
private fun PillButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, iconTrailing: Boolean = false) {
    Row(
        modifier = modifier
            .raisedSurface(RoundedCornerShape(RadiusCard), UaTheme.palette.surface1, shadow = false)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!iconTrailing) {
            Icon(
                icon,
                contentDescription = null,
                tint = UaTheme.palette.labelPrimary,
                modifier = Modifier.size(18.dp).padding(end = 8.dp),
            )
        }
        Text(text = label, style = Caption, color = UaTheme.palette.labelPrimary)
        if (iconTrailing) {
            Icon(
                icon,
                contentDescription = null,
                tint = UaTheme.palette.labelPrimary,
                modifier = Modifier.size(18.dp).padding(start = 8.dp),
            )
        }
    }
}

/** Shown while casting has some reason it isn't reaching the receiver - either a codec
 * [com.uacastplayer.cast.CastCompatibilityPolicy] flagged as incompatible, or the receiver
 * rejecting/erroring on the proxy fallback for any other reason. Local playback keeps playing
 * regardless, this only explains why the receiver isn't. Clears itself once the relevant
 * [PlayerUiState] field goes back to its default (new channel, cast disconnect, or - for a codec
 * incompatibility - the receiver recovering). */
@Composable
private fun CastIncompatibilityBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .raisedSurface(RoundedCornerShape(RadiusCard), UaTheme.palette.surface1, shadow = false)
            .padding(horizontal = CardPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            AppIcons.HelpCircle,
            contentDescription = null,
            tint = UaTheme.palette.routeAmber,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = Caption,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.padding(start = GapM),
        )
    }
}

@Composable
private fun ChannelInfoCard(
    channel: M3uChannel,
    badges: PlaybackBadgesState,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHPadding)
            .raisedSurface(
                RoundedCornerShape(RadiusCard),
                UaTheme.palette.surface1,
                edgeColor = UaTheme.palette.hairline,
                shadow = true,
            )
            .padding(CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelIcon(channel = channel, resolveIcon = resolveIcon, size = 64.dp, refreshKey = iconRefreshKey)
        Column(modifier = Modifier.padding(start = GapM).weight(1f)) {
            Text(text = channel.displayName, style = DisplayName, color = UaTheme.palette.labelPrimary, maxLines = 1)
            BadgesRow(badges, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun QuickSettingsRow(
    onAudioClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onQualityClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onGuideClick: () -> Unit,
    onPreviousChannelClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHPadding, vertical = GapM)
            .raisedSurface(
                RoundedCornerShape(RadiusCard),
                UaTheme.palette.surface1,
                edgeColor = UaTheme.palette.hairline,
                shadow = true,
            )
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Only shown once there's actually somewhere to jump back to - see
        // PlayerUiState.hasPreviousChannel. Each item gets an equal weight() share of the row so a
        // 6th item (this one) doesn't squeeze the others' labels into character-by-character wrap -
        // see docs/DESIGN_SYSTEM.md "§E Equal-share rows".
        onPreviousChannelClick?.let {
            QuickSettingItem(
                AppIcons.Refresh,
                stringResource(R.string.player_previous_channel),
                it,
                modifier = Modifier.weight(1f),
            )
        }
        QuickSettingItem(
            AppIcons.Storage,
            stringResource(R.string.player_audio_track),
            onAudioClick,
            modifier = Modifier.weight(1f),
        )
        QuickSettingItem(
            AppIcons.HelpCircle,
            stringResource(R.string.player_subtitle_track),
            onSubtitlesClick,
            modifier = Modifier.weight(1f),
        )
        QuickSettingItem(
            AppIcons.Image,
            stringResource(R.string.player_quality),
            onQualityClick,
            modifier = Modifier.weight(1f),
        )
        QuickSettingItem(
            AppIcons.Fullscreen,
            stringResource(R.string.player_aspect_ratio),
            onAspectRatioClick,
            modifier = Modifier.weight(1f),
        )
        QuickSettingItem(
            AppIcons.Tv,
            stringResource(R.string.player_tv_guide),
            onGuideClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowScope.QuickSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .raisedSurface(RoundedCornerShape(12.dp), UaTheme.palette.surface2, shadow = false),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = UaTheme.palette.azure, modifier = Modifier.size(18.dp))
        }
        Text(
            text = label,
            style = Caption,
            color = UaTheme.palette.labelSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun NextChannelsRail(
    channels: List<IndexedChannel>,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    onSelect: (IndexedChannel) -> Unit,
) {
    Column(modifier = Modifier.padding(top = GapM)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.player_next_channels_title),
                style = Title,
                color = UaTheme.palette.labelPrimary,
            )
            Text(text = stringResource(R.string.player_view_all), style = Caption, color = UaTheme.palette.accentText)
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = GapM, start = ScreenHPadding, end = ScreenHPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(channels, key = { it.index }) { indexed ->
                Column(
                    // Inside a LazyRow - shadow = false, see docs/DESIGN_SYSTEM.md "§D Depth".
                    modifier = Modifier
                        .raisedSurface(RoundedCornerShape(RadiusCard), UaTheme.palette.surface1, shadow = false)
                        .clickable { onSelect(indexed) }
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ChannelIcon(
                        channel = indexed.channel,
                        resolveIcon = resolveIcon,
                        size = 64.dp,
                        refreshKey = iconRefreshKey,
                    )
                    Text(
                        text = indexed.channel.displayName,
                        style = Caption,
                        color = UaTheme.palette.labelPrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 8.dp).width(96.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackPickerDialog(
    title: String,
    tracks: List<SelectableTrack>,
    offLabel: String? = null,
    onSelectOff: (() -> Unit)? = null,
    onSelect: (SelectableTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (offLabel != null && onSelectOff != null) {
                    Text(
                        text = offLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelectOff).padding(vertical = 12.dp),
                    )
                }
                tracks.forEach { track ->
                    Text(
                        text = track.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (track.isSelected) UaTheme.palette.azure else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(track) }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_back)) }
        },
    )
}

@Composable
private fun PlayerControlsOverlay(
    uiState: PlayerUiState,
    isFullscreen: Boolean,
    sleepTimerRemainingMillis: Long?,
    onExit: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onSelectPreview: (IndexedChannel) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallRoundIconButton(
                icon = AppIcons.ArrowBack,
                onClick = onExit,
                contentDescription = stringResource(R.string.common_back),
                background = UaTheme.palette.scrimBackground,
            )
            Column(modifier = Modifier.padding(start = GapM).weight(1f)) {
                Text(text = uiState.currentChannel?.displayName.orEmpty(), color = Color.White, style = DisplayName)
                BadgesRow(uiState.badges)
            }
            LiveIndicator()
            PlayerCastButton(modifier = Modifier.padding(start = GapM))
        }

        Box(modifier = Modifier.weight(1f))

        if (uiState.nextChannelsPreview.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.nextChannelsPreview, key = { it.index }) { indexed ->
                    Text(
                        text = indexed.channel.displayName,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(RadiusItem))
                            .background(UaTheme.palette.scrimBackground)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .pointerInput(indexed.index) {
                                detectTapGestures { onSelectPreview(indexed) }
                            },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(
                icon = AppIcons.SkipPrevious,
                onClick = onPrevious,
                contentDescription = stringResource(R.string.player_previous),
            )
            GradientPlayButton(
                icon = if (uiState.isPlaying) AppIcons.Pause else AppIcons.Play,
                onClick = onPlayPause,
                contentDescription = playPauseLabel(uiState.isPlaying),
            )
            RoundIconButton(
                icon = AppIcons.SkipNext,
                onClick = onNext,
                contentDescription = stringResource(R.string.player_next),
            )
            Box(modifier = Modifier.weight(1f))
            SleepTimerButton(remainingMillis = sleepTimerRemainingMillis, onClick = onOpenSleepTimer)
            SmallRoundIconButton(
                icon = AppIcons.PictureInPicture,
                onClick = onEnterPip,
                contentDescription = stringResource(R.string.player_picture_in_picture),
                background = UaTheme.palette.scrimBackground,
            )
            SmallRoundIconButton(
                icon = if (isFullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen,
                onClick = onToggleFullscreen,
                contentDescription = stringResource(
                    if (isFullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen
                ),
                background = UaTheme.palette.scrimBackground,
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
private fun PlayerCastButton(modifier: Modifier = Modifier, background: Color = UaTheme.palette.scrimBackground) {
    Box(
        modifier = modifier
            .size(IconButtonSize)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        CastButton(modifier = Modifier.size(IconButtonSize))
    }
}

/**
 * Opens the sleep timer dialog. Shows a plain icon when idle; once a timer is running, widens into
 * a pill showing the live countdown instead, so the remaining time is visible without opening the
 * dialog.
 */
@Composable
private fun SleepTimerButton(remainingMillis: Long?, onClick: () -> Unit) {
    if (remainingMillis == null) {
        SmallRoundIconButton(
            icon = AppIcons.Timer,
            onClick = onClick,
            contentDescription = stringResource(R.string.player_sleep_timer),
            background = UaTheme.palette.scrimBackground,
        )
    } else {
        Row(
            modifier = Modifier
                .height(IconButtonSize)
                .clip(RoundedCornerShape(IconButtonSize / 2))
                .background(UaTheme.palette.scrimBackground)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.Timer,
                contentDescription = stringResource(R.string.player_sleep_timer),
                tint = UaTheme.palette.azure,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = SleepTimerFormatter.formatRemaining(remainingMillis),
                color = Color.White,
                style = Caption,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** §4 rule 3 - LIVE dot breathing between alpha/scale 1 and 0.3/0.8, reversed, on an infinite loop. */
@Composable
private fun LiveIndicator() {
    val transition = rememberInfiniteTransition(label = "liveBreathe")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(BreatheMs), repeatMode = RepeatMode.Reverse),
        label = "liveAlpha",
    )
    val dotScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(BreatheMs), repeatMode = RepeatMode.Reverse),
        label = "liveScale",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .scale(dotScale)
                .background(UaTheme.palette.routeRed.copy(alpha = alpha), CircleShape)
                .background(UaTheme.palette.redGlow.copy(alpha = alpha * 0.4f), CircleShape),
        )
        Text(
            text = stringResource(R.string.player_live_indicator),
            style = LiveText,
            color = UaTheme.palette.routeRed.copy(alpha = alpha.coerceAtLeast(0.6f)),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun playPauseLabel(isPlaying: Boolean) =
    stringResource(if (isPlaying) R.string.player_pause else R.string.player_play)

@Composable
private fun BadgesRow(badges: PlaybackBadgesState, modifier: Modifier = Modifier) {
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

private fun AudioChannelLayout.labelRes(): Int = when (this) {
    AudioChannelLayout.MONO -> R.string.audio_layout_mono
    AudioChannelLayout.STEREO -> R.string.audio_layout_stereo
    AudioChannelLayout.SURROUND_5_1 -> R.string.audio_layout_surround_5_1
    AudioChannelLayout.SURROUND_7_1 -> R.string.audio_layout_surround_7_1
    AudioChannelLayout.OTHER -> R.string.audio_layout_stereo
}

private object FullscreenController {
    fun apply(activity: Activity, enabled: Boolean) {
        activity.requestedOrientation = if (enabled) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(
            activity.window,
            activity.window.decorView,
        )
        if (enabled) {
            windowInsetsController.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
}

private object PipController {
    fun enter(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity.enterPictureInPictureMode(params)
        }
    }
}
