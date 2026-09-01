package com.uacastplayer.ui.player

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.uacastplayer.R
import com.uacastplayer.core.ui.findActivity
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.guidedtour.GuidedTourKeys
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.player.AudioChannelLayout
import com.uacastplayer.player.PlaybackBadgesState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.player.ResizeModeCycle
import com.uacastplayer.player.StallRetryPolicy
import com.uacastplayer.player.AutoSkipRecoveryState
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.components.PrimaryButton
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.cast.CastButton
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.components.liveRing
import com.uacastplayer.ui.dlna.DlnaDeviceSheet
import com.uacastplayer.ui.guidedtour.guidedTourTarget
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.DUR_PRESS
import com.uacastplayer.ui.theme.EaseSpring
import com.uacastplayer.ui.theme.IconButtonSize
import com.uacastplayer.ui.theme.PRESS_SCALE_ICON
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.raisedSurface
import java.io.File

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
    val dlnaState by viewModel.dlnaState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val environment = PlayerScreenEnvironment(
        activity = activity,
        audioManager = audioManager,
        haptics = LocalHapticFeedback.current,
    )
    val transientState = remember(activity, audioManager) {
        PlayerScreenTransientState(activity, audioManager)
    }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val content = PlayerScreenContent(
        uiState = uiState,
        dlnaState = dlnaState,
        iconRefreshKey = (epgState.data != null) to iconPrefetchState.completedRuns,
        videoResizeMode = ResizeModeCycle.toMedia3ResizeMode(uiState.resizeMode),
    )
    val actions = PlayerScreenActions(
        viewModel = viewModel,
        onExit = onExit,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
        resolveIcon = resolveIcon,
        onFullscreenChanged = { isFullscreen = it },
    )
    val sleepTimer = rememberSleepTimerState(onExpire = { viewModel.player.pause() })
    val configuration = LocalConfiguration.current
    val isInPip = remember(configuration, activity) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity?.isInPictureInPictureMode == true
    }

    PlayerScreenEffects(environment, isFullscreen, uiState, transientState)
    if (isInPip) {
        PipPlayerSurface(viewModel, modifier)
        return
    }
    if (isFullscreen) {
        FullscreenPlayerContent(
            content,
            actions,
            environment,
            transientState,
            sleepTimer,
            modifier,
        )
    } else {
        InlinePlayerContent(content, actions, transientState, modifier)
    }

    if (transientState.showDlnaSheet) {
        DlnaDeviceSheet(
            connectionState = dlnaState,
            discoverDevices = viewModel.dlna::discoverDevices,
            onDismiss = { transientState.showDlnaSheet = false },
            onDeviceSelected = viewModel.dlna::connect,
            onStopCasting = viewModel.dlna::stop,
            onVolumeChange = viewModel.dlna::setVolume,
        )
    }
    PlayerDialogs(
        viewModel = viewModel,
        uiState = uiState,
        epgState = epgState,
        sleepTimer = sleepTimer,
        currentChannel = uiState.currentChannel,
        showSleepTimerDialog = transientState.showSleepTimerDialog,
        onDismissSleepTimerDialog = { transientState.showSleepTimerDialog = false },
        showAudioDialog = transientState.showAudioDialog,
        onDismissAudioDialog = { transientState.showAudioDialog = false },
        showSubtitleDialog = transientState.showSubtitleDialog,
        onDismissSubtitleDialog = { transientState.showSubtitleDialog = false },
        showQualityDialog = transientState.showQualityDialog,
        onDismissQualityDialog = { transientState.showQualityDialog = false },
        showGuideSheet = transientState.showGuideSheet,
        onDismissGuideSheet = { transientState.showGuideSheet = false },
    )
}

@OptIn(markerClass = [UnstableApi::class])
@Composable
private fun PipPlayerSurface(viewModel: PlayerViewModel, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                player = viewModel.player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { if (it.player !== viewModel.player) it.player = viewModel.player },
        onRelease = { it.player = null },
        modifier = modifier.fillMaxSize(),
    )
}

/** Playback recovery status shared by fullscreen and inline surfaces. */
@Composable
internal fun RecoveringPlaybackIndicator(
    attempt: Int,
    onPickAnotherChannel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusCard))
            .background(UaTheme.palette.scrimBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
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
                    .padding(top = 6.dp)
                    .minimumInteractiveComponentSize()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.player_pick_another_channel),
                        onClick = onPickAnotherChannel,
                    )
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

/** Explains the otherwise surprising automatic channel hop and lets the user stop the sequence. */
@Composable
internal fun AutoSkipRecoveryIndicator(
    state: AutoSkipRecoveryState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusCard))
            .background(UaTheme.palette.scrimBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
            Text(
                text = stringResource(
                    R.string.player_auto_skip_progress,
                    state.skippedChannels,
                    state.totalChannels,
                ),
                color = Color.White,
                style = Caption,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Text(
            text = stringResource(R.string.common_cancel),
            color = UaTheme.palette.azure,
            style = Caption,
            modifier = Modifier
                .padding(top = 6.dp)
                .minimumInteractiveComponentSize()
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.common_cancel),
                    onClick = onCancel,
                )
                .padding(horizontal = 8.dp),
        )
    }
}

/** Terminal playback error with direct recovery actions, shared by inline and fullscreen video. */
@Composable
internal fun PlaybackFailureCard(
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusCard))
            .background(UaTheme.palette.scrimBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.player_fatal_error),
            color = Color.White,
            style = BodyText,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            text = stringResource(R.string.player_retry),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        SecondaryButton(
            text = stringResource(R.string.player_next),
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.player_back_to_channels),
            color = UaTheme.palette.azure,
            style = Caption,
            modifier = Modifier
                .padding(top = 8.dp)
                .minimumInteractiveComponentSize()
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.player_back_to_channels),
                    onClick = onExit,
                )
                .padding(horizontal = 8.dp),
        )
    }
}

@Composable
internal fun PlayerCastButton(
    modifier: Modifier = Modifier,
    background: Color = UaTheme.palette.scrimBackground,
    isCasting: Boolean = false,
) {
    val castDescription = stringResource(
        if (isCasting) R.string.player_chromecast_connected else R.string.player_chromecast_cast,
    )
    val castStateDescription = stringResource(R.string.cast_status_connected)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE_ICON else 1f,
        animationSpec = tween(DUR_PRESS, easing = EaseSpring),
        label = "castButtonScale",
    )
    Box(
        modifier = modifier
            .size(IconButtonSize)
            .guidedTourTarget(GuidedTourKeys.CAST_BUTTON)
            .scale(scale)
            .liveRing(active = isCasting, color = UaTheme.palette.azure)
            .semantics(mergeDescendants = true) {
                contentDescription = castDescription
                role = Role.Button
                if (isCasting) stateDescription = castStateDescription
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        pressed = event.changes.any { it.pressed }
                    }
                }
            }
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
            style = Caption,
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
