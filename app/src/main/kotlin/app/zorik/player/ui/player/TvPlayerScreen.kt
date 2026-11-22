@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package app.zorik.player.ui.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalAccessibilityManager
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zorik.player.R
import app.zorik.player.epg.EpgUiState
import app.zorik.player.player.PlayerUiState
import app.zorik.player.player.PlayerViewModel
import app.zorik.player.player.ResizeModeCycle
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.ui.components.PrimaryButton
import app.zorik.player.ui.components.SecondaryButton
import app.zorik.player.ui.remote.LocalOpenRemote
import app.zorik.player.ui.theme.Title
import app.zorik.player.ui.theme.UaTheme

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
    val canAutoHide = state.isPlaying && !state.fatalError
    val timeout = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
        TV_CONTROLS_TIMEOUT_MILLIS, containsIcons = true, containsText = true, containsControls = true,
    )?.coerceAtLeast(TV_CONTROLS_TIMEOUT_MILLIS) ?: TV_CONTROLS_TIMEOUT_MILLIS
    val touchExploration = rememberTouchExploration()
    PlayerControlsIdleTimer(visible, canAutoHide, action != null || touchExploration, interaction, timeout) {
        visible = false
    }
    LaunchedEffect(state.isPlaying, state.fatalError) {
        if (!state.isPlaying || state.fatalError) visible = true
    }
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
            controlsFocus, epgState, onAction = { action = it }, modifier = Modifier.align(Alignment.BottomCenter))
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
    epgState: EpgUiState, onAction: (Int) -> Unit, modifier: Modifier) {
            val programme = rememberPlayerProgramme(state.currentChannel, epgState)
            Column(modifier.fillMaxWidth().padding(16.dp)
                .background(Brush.verticalGradient(listOf(UaTheme.palette.surface1.copy(alpha = 0.90f),
                    UaTheme.palette.void.copy(alpha = 0.97f))), RoundedCornerShape(24.dp))
                .border(1.dp, UaTheme.palette.overlayHighlight, RoundedCornerShape(24.dp))
                .padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(state.currentChannel?.displayName.orEmpty(), style = Title, color = UaTheme.palette.labelPrimary)
                programme?.let { PlayerProgrammePanel(it, epgState.nowMillis) }
                if (state.fatalError) {
                    if (state.currentChannel?.localVideo != null) {
                        Text(stringResource(R.string.local_video_playback_failed))
                    }
                    PrimaryButton(stringResource(R.string.common_retry), viewModel::retryCurrentChannel)
                }
                TvPlayerControls(state, onExit, viewModel::togglePlayback, viewModel.navigation::requestPrevious,
                    viewModel.navigation::requestNext, controlsFocus)
                if ((state.currentChannel?.localVideo != null || state.canSeek) && state.canControlPlayback) {
                    FilePlaybackSeekBar(viewModel, state)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(4.dp)) {
                    item { SecondaryButton(stringResource(R.string.tv_pair_phone), LocalOpenRemote.current) }
                    item {
                        state.currentChannel?.takeIf { it.localVideo == null }?.let { channel ->
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
                        if (playerActionVisible(label, false, state.currentChannel?.localVideo != null)) {
                            SecondaryButton(stringResource(label), { onAction(label) })
                        }
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
        val isFile = state.currentChannel?.localVideo != null
        SecondaryButton(stringResource(if (isFile) R.string.common_back else R.string.nav_channels),
            onExit, Modifier.focusRequester(focus))
        if (!isFile) {
            SecondaryButton(stringResource(R.string.player_previous), onPrevious, enabled = state.canGoPrevious)
        }
        if (state.canControlPlayback) PrimaryButton(playPauseLabel(state.wantsToPlay), onPlayPause)
        if (!isFile) SecondaryButton(stringResource(R.string.player_next), onNext, enabled = state.canGoNext)
    }
}

private val TV_REVEAL_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
private val TV_PLAYER_ACTIONS = listOf(R.string.player_audio_track, R.string.player_subtitle_track,
    R.string.player_quality, R.string.player_tv_guide, R.string.player_sleep_timer)
