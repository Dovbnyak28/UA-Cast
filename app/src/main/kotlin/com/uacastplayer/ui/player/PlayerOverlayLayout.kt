package com.uacastplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.uacastplayer.R
import com.uacastplayer.guidedtour.GuidedTourKeys
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.ui.components.GradientPlayButton
import com.uacastplayer.ui.components.RoundIconButton
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.components.liveRing
import com.uacastplayer.ui.guidedtour.guidedTourTarget
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.DisplayName
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaTheme

@Composable
internal fun PlayerOverlayHeader(
    state: PlayerUiState, onExit: () -> Unit, isDlnaCasting: Boolean,
    onDevices: () -> Unit, onActions: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < COMPACT_HEADER_WIDTH ||
            (maxWidth < LARGE_TEXT_HEADER_WIDTH && LocalDensity.current.fontScale > LARGE_TEXT_SCALE)
        Column(Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SmallRoundIconButton(AppIcons.ArrowBack, onExit,
                    contentDescription = stringResource(R.string.common_back),
                    background = UaTheme.palette.scrimBackground)
                if (stacked) Box(Modifier.weight(1f)) else OverlayMediaName(state, Modifier.weight(1f))
                if (state.currentChannel?.localVideo == null) LiveIndicator()
                PlayerCastButton(isCasting = state.isCasting,
                    modifier = Modifier.guidedTourTarget(GuidedTourKeys.CAST_BUTTON))
                SmallRoundIconButton(AppIcons.Tv, onDevices,
                    contentDescription = stringResource(R.string.dlna_sheet_title),
                    background = UaTheme.palette.scrimBackground,
                    tint = if (isDlnaCasting) UaTheme.palette.azure else UaTheme.palette.labelPrimary,
                    modifier = Modifier.liveRing(active = isDlnaCasting, color = UaTheme.palette.azure))
                SmallRoundIconButton(AppIcons.More, onActions,
                    contentDescription = stringResource(R.string.player_more_controls),
                    background = UaTheme.palette.scrimBackground)
            }
            if (stacked) OverlayMediaName(state, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun OverlayMediaName(state: PlayerUiState, modifier: Modifier) {
    Column(modifier) {
        Text(state.currentChannel?.displayName.orEmpty(), color = UaTheme.palette.labelPrimary,
            style = DisplayName, maxLines = if (state.currentChannel?.localVideo != null) 2 else 1,
            overflow = TextOverflow.Ellipsis)
        BadgesRow(state.badges)
    }
}

@Composable
internal fun PlayerOverlayFooter(
    state: PlayerUiState, fullscreen: Boolean, timer: State<Long?>, actions: PlayerOverlayFooterActions,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < COMPACT_CONTROLS_WIDTH) {
            Column(Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically) { OverlayTransport(state, actions) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    SleepTimerButton(timer, actions.sleepTimer)
                    Box(Modifier.weight(1f))
                    OverlayWindowActions(fullscreen, actions)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                OverlayTransport(state, actions)
                Box(Modifier.weight(1f))
                SleepTimerButton(timer, actions.sleepTimer)
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
    GradientPlayButton(if (state.wantsToPlay) AppIcons.Pause else AppIcons.Play, actions.playPause,
        enabled = state.canControlPlayback, contentDescription = playPauseLabel(state.wantsToPlay))
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

private val COMPACT_CONTROLS_WIDTH = 600.dp
private val COMPACT_HEADER_WIDTH = 420.dp
private val LARGE_TEXT_HEADER_WIDTH = 480.dp
private const val LARGE_TEXT_SCALE = 1.4f
