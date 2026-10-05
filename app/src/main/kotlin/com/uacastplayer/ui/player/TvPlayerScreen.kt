@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.uacastplayer.ui.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.R
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.player.ResizeModeCycle
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.components.PrimaryButton
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.remote.LocalOpenRemote
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.UaTheme
import kotlinx.coroutines.delay

private const val TV_CONTROLS_TIMEOUT_MILLIS = 8_000L

@Composable
internal fun TvPlayerScreen(
    viewModel: PlayerViewModel,
    onExit: () -> Unit,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    epgState: EpgUiState,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val surfaceFocus = remember { FocusRequester() }
    val controlsFocus = remember { FocusRequester() }
    var visible by remember { mutableStateOf(true) }
    var action by remember { mutableStateOf<Int?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    val sleepTimer = rememberSleepTimerState(viewModel.sleepTimer)
    val view = LocalView.current
    DisposableEffect(view, state.isPlaying) {
        view.keepScreenOn = state.isPlaying
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(visible) {
        if (visible) controlsFocus.requestFocus() else surfaceFocus.requestFocus()
    }
    val canAutoHide = state.wantsToPlay && !state.fatalError
    LaunchedEffect(visible, canAutoHide, action, interaction) {
        if (visible && canAutoHide && action == null) {
            delay(TV_CONTROLS_TIMEOUT_MILLIS)
            visible = false
        }
    }
    LaunchedEffect(state.fatalError) { if (state.fatalError) visible = true }
    BackHandler { if (visible) visible = false else onExit() }
    Box(modifier.fillMaxSize().background(Color.Black)
        .focusRequester(surfaceFocus).onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.action == KeyEvent.ACTION_DOWN) interaction++
            handleTvPlayerKey(key, visible, viewModel, onReveal = { visible = true })
        }.focusable()) {
        VideoSurface(viewModel, ResizeModeCycle.toMedia3ResizeMode(state.resizeMode), Modifier.fillMaxSize())
        if (state.isBuffering || state.isRecoveringPlayback) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (visible) TvPlayerOverlay(state, viewModel, onExit, isFavorite, onToggleFavorite,
            controlsFocus, onAction = { action = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }
    PlayerDialogs(viewModel, state, epgState, sleepTimer, state.currentChannel,
        action == R.string.player_sleep_timer, { action = null },
        action == R.string.player_audio_track, { action = null },
        action == R.string.player_subtitle_track, { action = null },
        action == R.string.player_quality, { action = null },
        action == R.string.player_tv_guide, { action = null })
}

private fun handleTvPlayerKey(key: KeyEvent, visible: Boolean, viewModel: PlayerViewModel,
    onReveal: () -> Unit): Boolean {
    val mediaAction = tvMediaAction(key.keyCode, viewModel)
    return when {
        mediaAction != null -> {
            if (key.action == KeyEvent.ACTION_UP) mediaAction()
            true
        }
        !visible && key.keyCode in TV_REVEAL_KEYS -> {
            if (key.action == KeyEvent.ACTION_UP) {
                if (key.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) viewModel.togglePlayback()
                onReveal()
            }
            true
        }
        else -> false
    }
}

private fun tvMediaAction(keyCode: Int, viewModel: PlayerViewModel): (() -> Unit)? = when (keyCode) {
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> viewModel::togglePlayback
    KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> viewModel.navigation::requestNext
    KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> viewModel.navigation::requestPrevious
    else -> null
}

@Composable
private fun TvPlayerOverlay(state: PlayerUiState, viewModel: PlayerViewModel, onExit: () -> Unit,
    isFavorite: (M3uChannel) -> Boolean, onToggleFavorite: (M3uChannel) -> Unit, controlsFocus: FocusRequester,
    onAction: (Int) -> Unit, modifier: Modifier) {
            Column(modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.85f))
                .padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(state.currentChannel?.displayName.orEmpty(), style = Title, color = UaTheme.palette.labelPrimary)
                if (state.fatalError) {
                    PrimaryButton(stringResource(R.string.common_retry), viewModel::retryCurrentChannel)
                }
                TvPlayerControls(state, onExit, viewModel::togglePlayback, viewModel.navigation::requestPrevious,
                    viewModel.navigation::requestNext, controlsFocus)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(4.dp)) {
                    item { SecondaryButton(stringResource(R.string.tv_pair_phone), LocalOpenRemote.current) }
                    item {
                        state.currentChannel?.let { channel ->
                            val favoriteLabel = if (isFavorite(channel)) R.string.tv_remove_favorite
                                else R.string.tv_add_favorite
                            SecondaryButton(stringResource(favoriteLabel), { onToggleFavorite(channel) })
                        }
                    }
                    item {
                        SecondaryButton(stringResource(R.string.player_aspect_ratio),
                            viewModel.navigation::cycleResizeMode)
                    }
                    items(TV_PLAYER_ACTIONS.size) { index ->
                        val label = TV_PLAYER_ACTIONS[index]
                        SecondaryButton(stringResource(label), { onAction(label) })
                    }
                }
            }
}

@Composable
internal fun TvPlayerControls(
    state: PlayerUiState,
    onExit: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    focus: FocusRequester,
) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SecondaryButton(stringResource(R.string.nav_channels), onExit, Modifier.focusRequester(focus))
        SecondaryButton(stringResource(R.string.player_previous), onPrevious, enabled = state.canGoPrevious)
        PrimaryButton(playPauseLabel(state.wantsToPlay), onPlayPause, enabled = state.canControlPlayback)
        SecondaryButton(stringResource(R.string.player_next), onNext, enabled = state.canGoNext)
    }
}

private val TV_REVEAL_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
private val TV_PLAYER_ACTIONS = listOf(R.string.player_audio_track, R.string.player_subtitle_track,
    R.string.player_quality, R.string.player_tv_guide, R.string.player_sleep_timer)
