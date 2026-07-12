package com.uacastplayer.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.uacastplayer.R
import com.uacastplayer.core.ui.findActivity
import com.uacastplayer.player.AudioChannelLayout
import com.uacastplayer.player.IndexedChannel
import com.uacastplayer.player.PlaybackBadgesState
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.ui.theme.AppIcons
import kotlinx.coroutines.delay

private const val CONTROLS_AUTO_HIDE_MILLIS = 3000L

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
                onExit = onExit,
                onPlayPause = {
                    if (viewModel.player.isPlaying) viewModel.player.pause() else viewModel.player.play()
                },
                onNext = viewModel::requestNext,
                onPrevious = viewModel::requestPrevious,
                onToggleFullscreen = { isFullscreen = !isFullscreen },
                onEnterPip = { activity?.let(PipController::enter) },
                onSelectPreview = { indexed -> viewModel.requestSwitch(indexed.index) },
            )
        }
    }
}

@Composable
private fun PlayerControlsOverlay(
    uiState: PlayerUiState,
    isFullscreen: Boolean,
    onExit: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit,
    onSelectPreview: (IndexedChannel) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = uiState.currentChannel?.displayName.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                BadgesRow(uiState.badges)
            }
        }

        Box(modifier = Modifier.weight(1f))

        if (uiState.nextChannelsPreview.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.nextChannelsPreview, key = { it.index }) { indexed ->
                    Text(
                        text = indexed.channel.displayName,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
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
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(AppIcons.SkipPrevious, contentDescription = stringResource(R.string.player_previous), tint = Color.White)
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (uiState.isPlaying) AppIcons.Pause else AppIcons.Play,
                    contentDescription = playPauseLabel(uiState.isPlaying),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onNext) {
                Icon(AppIcons.SkipNext, contentDescription = stringResource(R.string.player_next), tint = Color.White)
            }
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = onEnterPip) {
                Icon(AppIcons.PictureInPicture, contentDescription = stringResource(R.string.player_picture_in_picture), tint = Color.White)
            }
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    if (isFullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen,
                    contentDescription = stringResource(
                        if (isFullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen
                    ),
                    tint = Color.White,
                )
            }
        }
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
            color = Color(0xFFB5BFCF),
            style = MaterialTheme.typography.labelLarge,
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
