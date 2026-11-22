@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package app.zorik.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import app.zorik.player.ui.tv.tvFocus
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.BodyText
import app.zorik.player.ui.theme.panelSurface
import app.zorik.player.R
import app.zorik.player.dlna.DlnaConnectionState
import app.zorik.player.player.PlayerUiState
import app.zorik.player.ui.theme.ScreenHPadding
import app.zorik.player.ui.theme.Title
import app.zorik.player.ui.theme.UaTheme

/** One action catalog for both video layouts, independent of Media3's runtime ownership. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerActionsSheet(
    state: PlayerScreenTransientState,
    actions: PlayerScreenActions,
    hasPreviousChannel: Boolean,
    isRemote: Boolean = false,
    isFile: Boolean = false,
) {
    ModalBottomSheet(onDismissRequest = { state.showActionsSheet = false }, containerColor = UaTheme.palette.surface2,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenHPadding)) {
            Text(stringResource(R.string.player_more_controls), style = Title)
            if (isRemote) Text(stringResource(R.string.player_remote_controls_notice))
            if (hasPreviousChannel && !isFile) {
                PlayerActionRow(stringResource(R.string.player_previous_channel), AppIcons.SkipPrevious) {
                    state.showActionsSheet = false
                    actions.viewModel.navigation.requestPreviousChannel()
                }
            }
            listOf(
                Triple(R.string.player_levels, AppIcons.Brightness) { state.showLevelsSheet = true },
                Triple(R.string.player_audio_track, AppIcons.AudioTrack) { state.showAudioDialog = true },
                Triple(R.string.player_subtitle_track, AppIcons.Subtitles) { state.showSubtitleDialog = true },
                Triple(R.string.player_tv_guide, AppIcons.Guide) { state.showGuideSheet = true },
                Triple(R.string.player_quality, AppIcons.Quality) { state.showQualityDialog = true },
                Triple(R.string.player_aspect_ratio, AppIcons.AspectRatio) {
                    actions.viewModel.navigation.cycleResizeMode()
                    state.resizeModeToastNonce++
                },
                Triple(R.string.player_sleep_timer, AppIcons.Timer) { state.showSleepTimerDialog = true },
                Triple(R.string.player_view_all, AppIcons.Channels) { state.showChannelsSheet = true },
            ).filter { (label, _, _) -> playerActionVisible(label, isRemote, isFile) }
                .forEach { (label, icon, action) ->
                    PlayerActionRow(stringResource(label), icon) {
                        state.showActionsSheet = false
                        action()
                    }
                }
        }
    }
}

private const val ACTION_CHEVRON_ROTATION = -90f

@Composable
private fun PlayerActionRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).panelSurface(shape).tvFocus(shape)
        .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
        .heightIn(min = 56.dp).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).background(UaTheme.palette.azure.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = UaTheme.palette.azure, modifier = Modifier.size(20.dp))
        }
        Text(label, style = BodyText, color = UaTheme.palette.labelPrimary, modifier = Modifier.weight(1f))
        Icon(AppIcons.ChevronDown, null, tint = UaTheme.palette.labelSecondary,
            modifier = Modifier.size(16.dp).rotate(ACTION_CHEVRON_ROTATION))
    }
}

internal fun playerActionAvailableRemotely(label: Int): Boolean = label == R.string.player_tv_guide ||
    label == R.string.player_sleep_timer || label == R.string.player_view_all

internal fun playerActionVisible(label: Int, remote: Boolean, file: Boolean): Boolean {
    val channelAction = label == R.string.player_tv_guide || label == R.string.player_view_all ||
        label == R.string.player_previous_channel
    return !(file && channelAction) && (!remote || playerActionAvailableRemotely(label))
}

internal fun remotePlaybackOwnsControls(player: PlayerUiState, dlna: DlnaConnectionState): Boolean =
    player.isCasting || dlna.isConnecting || dlna.connectedDevice != null

@Composable
internal fun FilePlaybackDialogEffect(isFile: Boolean, state: PlayerScreenTransientState) {
    LaunchedEffect(isFile) {
        if (isFile) {
            state.showGuideSheet = false
            state.showChannelsSheet = false
        }
    }
}

@Composable
internal fun RemotePlaybackDialogEffect(isRemote: Boolean, state: PlayerScreenTransientState) {
    LaunchedEffect(isRemote) {
        if (isRemote) {
            state.showLevelsSheet = false
            state.showAudioDialog = false
            state.showSubtitleDialog = false
            state.showQualityDialog = false
        }
    }
}
