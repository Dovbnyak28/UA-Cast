package app.zorik.player.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import app.zorik.player.R
import app.zorik.player.player.PlayerUiState
import app.zorik.player.ui.components.GradientPlayButton
import app.zorik.player.ui.components.RoundIconButton
import app.zorik.player.ui.components.SmallRoundIconButton
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.DisplayName
import app.zorik.player.ui.theme.Caption
import app.zorik.player.ui.theme.ScreenHPadding
import app.zorik.player.ui.theme.UaTheme

/** UI presentation only; inline and TV playback keep their regular controls. */
internal val LocalCompactPlayerControls = staticCompositionLocalOf { false }
internal val PLAYER_WIDE_CONTROLS_WIDTH = 600.dp

@Composable
internal fun PlayerOverlayHeader(
    state: PlayerUiState, onExit: () -> Unit, isDlnaCasting: Boolean,
    onDevices: () -> Unit, onActions: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = LocalCompactPlayerControls.current
        val stacked = !compact && (maxWidth < COMPACT_HEADER_WIDTH ||
            (maxWidth < LARGE_TEXT_HEADER_WIDTH && LocalDensity.current.fontScale > LARGE_TEXT_SCALE))
        Column(Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding,
            vertical = if (compact) 4.dp else 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SmallRoundIconButton(AppIcons.ArrowBack, onExit,
                    contentDescription = stringResource(R.string.common_back),
                    background = UaTheme.palette.scrimBackground)
                if (stacked) Box(Modifier.weight(1f)) else OverlayMediaName(state, Modifier.weight(1f))
                if (!stacked && showsBroadcastBadge(state)) LiveIndicator()
                PlayerCastButton(isCasting = state.isCasting)
                OtherTvButton(isDlnaCasting, onDevices)
                SmallRoundIconButton(AppIcons.More, onActions,
                    contentDescription = stringResource(R.string.player_more_controls),
                    background = UaTheme.palette.scrimBackground)
            }
            if (stacked) Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OverlayMediaName(state, Modifier.weight(1f))
                if (showsBroadcastBadge(state)) LiveIndicator()
            }
        }
    }
}

private fun showsBroadcastBadge(state: PlayerUiState): Boolean =
    state.currentChannel?.localVideo == null && !state.canSeek

@Composable
private fun OverlayMediaName(state: PlayerUiState, modifier: Modifier) {
    val compact = LocalCompactPlayerControls.current
    val description = stringResource(R.string.player_now_playing, state.currentChannel?.displayName.orEmpty())
    Column(modifier) {
        Text(state.currentChannel?.displayName.orEmpty(), color = UaTheme.palette.labelPrimary,
            style = if (compact) Caption else DisplayName,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading(); contentDescription = description })
        if (!compact) BadgesRow(state.badges)
    }
}

@Composable
internal fun PlayerOverlayFooter(
    state: PlayerUiState, fullscreen: Boolean, timer: State<Long?>, actions: PlayerOverlayFooterActions,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val verticalPadding = if (LocalCompactPlayerControls.current) 4.dp else 12.dp
        if (maxWidth < PLAYER_WIDE_CONTROLS_WIDTH && !LocalCompactPlayerControls.current) {
            Column(Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically) { OverlayTransport(state, actions) }
                FlowRow(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SleepTimerButton(timer, actions.sleepTimer)
                    OverlayGuideAction(actions)
                    OverlayWindowActions(fullscreen, actions)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = verticalPadding),
                horizontalArrangement = Arrangement.spacedBy(
                    if (LocalCompactPlayerControls.current || LocalDensity.current.fontScale > LARGE_TEXT_SCALE) 8.dp
                    else 24.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OverlayTransport(state, actions)
                Box(Modifier.weight(1f))
                SleepTimerButton(timer, actions.sleepTimer)
                OverlayGuideAction(actions)
                OverlayWindowActions(fullscreen, actions)
            }
        }
    }
}

@Composable
private fun OverlayTransport(state: PlayerUiState, actions: PlayerOverlayFooterActions) {
    val file = state.currentChannel?.localVideo != null
    if (!file) RoundIconButton(AppIcons.SkipPrevious, actions.previous, enabled = state.canGoPrevious,
        contentDescription = stringResource(R.string.player_previous))
    if (state.canControlPlayback) {
        GradientPlayButton(if (state.wantsToPlay) AppIcons.Pause else AppIcons.Play, actions.playPause,
            contentDescription = playPauseLabel(state.wantsToPlay))
    }
    if (!file) RoundIconButton(AppIcons.SkipNext, actions.next, enabled = state.canGoNext,
        contentDescription = stringResource(R.string.player_next))
}

@Composable
private fun OverlayWindowActions(fullscreen: Boolean, actions: PlayerOverlayFooterActions) {
    SmallRoundIconButton(AppIcons.PictureInPicture, actions.pip,
        contentDescription = stringResource(R.string.player_picture_in_picture),
        background = UaTheme.palette.scrimBackground)
    SmallRoundIconButton(if (fullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen, actions.fullscreen,
        contentDescription = stringResource(
            if (fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen,
        ),
        background = UaTheme.palette.scrimBackground)
}

@Composable
private fun OverlayGuideAction(actions: PlayerOverlayFooterActions) {
    actions.guide?.let { open ->
        SmallRoundIconButton(AppIcons.Guide, open,
            contentDescription = stringResource(R.string.player_tv_guide),
            background = UaTheme.palette.scrimBackground)
    }
}

private val COMPACT_HEADER_WIDTH = 420.dp
private val LARGE_TEXT_HEADER_WIDTH = 480.dp
private const val LARGE_TEXT_SCALE = 1.4f
