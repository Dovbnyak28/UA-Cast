package com.uacastplayer.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.uacastplayer.core.ui.findActivity
import com.uacastplayer.player.AudioChannelLayout
import com.uacastplayer.player.IndexedChannel
import com.uacastplayer.player.PlaybackBadgesState
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.player.SleepTimerFormatter
import com.uacastplayer.ui.components.GradientPlayButton
import com.uacastplayer.ui.components.RoundIconButton
import com.uacastplayer.ui.components.SleepTimerDialog
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.Azure
import com.uacastplayer.ui.theme.BreatheMs
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.IconButtonSize
import com.uacastplayer.ui.theme.LabelSecondary
import com.uacastplayer.ui.theme.LiveText
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.RouteRed
import com.uacastplayer.ui.theme.RedGlow
import com.uacastplayer.ui.theme.ScreenHPadding
import kotlinx.coroutines.delay

private const val CONTROLS_AUTO_HIDE_MILLIS = 3000L

@OptIn(markerClass = [UnstableApi::class])
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val sleepTimer = rememberSleepTimerState(onExpire = { viewModel.player.pause() })

    val configuration = LocalConfiguration.current
    val isInPip = remember(configuration, activity) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity?.isInPictureInPictureMode == true
    }

    DisposableEffect(activity, isFullscreen) {
        activity?.let { FullscreenController.apply(it, isFullscreen) }
        onDispose { activity?.let { FullscreenController.apply(it, enabled = false) } }
    }

    LaunchedEffect(controlsVisible, uiState.isPlaying) {
        if (controlsVisible && uiState.isPlaying) {
            delay(CONTROLS_AUTO_HIDE_MILLIS)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { controlsVisible = !controlsVisible } },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

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

        if (!isInPip && controlsVisible) {
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
    }
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
                background = Color(0x66000000),
            )
            Column(modifier = Modifier.padding(start = GapM).weight(1f)) {
                Text(text = uiState.currentChannel?.displayName.orEmpty(), color = Color.White, style = CardTitle)
                BadgesRow(uiState.badges)
            }
            LiveIndicator()
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
                            .background(Color(0x66000000))
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
                background = Color(0x66000000),
            )
            SmallRoundIconButton(
                icon = if (isFullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen,
                onClick = onToggleFullscreen,
                contentDescription = stringResource(
                    if (isFullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen
                ),
                background = Color(0x66000000),
            )
        }
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
            background = Color(0x66000000),
        )
    } else {
        Row(
            modifier = Modifier
                .height(IconButtonSize)
                .clip(RoundedCornerShape(IconButtonSize / 2))
                .background(Color(0x66000000))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.Timer,
                contentDescription = stringResource(R.string.player_sleep_timer),
                tint = Azure,
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
                .background(RouteRed.copy(alpha = alpha), CircleShape)
                .background(RedGlow.copy(alpha = alpha * 0.4f), CircleShape),
        )
        Text(
            text = stringResource(R.string.player_live_indicator),
            style = LiveText,
            color = RouteRed.copy(alpha = alpha.coerceAtLeast(0.6f)),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun playPauseLabel(isPlaying: Boolean) =
    stringResource(if (isPlaying) R.string.player_pause else R.string.player_play)

@Composable
private fun BadgesRow(badges: PlaybackBadgesState) {
    val parts = buildList {
        badges.qualityLabel?.let(::add)
        badges.videoCodecLabel?.let(::add)
        badges.audioCodecLabel?.let(::add)
        badges.channelLayout?.let { add(stringResource(it.labelRes())) }
    }
    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString(" · "),
            color = LabelSecondary,
            style = com.uacastplayer.ui.theme.Caption,
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
