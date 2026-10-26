@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.uacastplayer.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.UaTheme

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
    ModalBottomSheet(onDismissRequest = { state.showActionsSheet = false }, containerColor = UaTheme.palette.surface2) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenHPadding)) {
            Text(stringResource(R.string.player_more_controls), style = Title)
            if (isRemote) Text(stringResource(R.string.player_remote_controls_notice))
            if (hasPreviousChannel && !isFile) {
                SecondaryButton(
                    text = stringResource(R.string.player_previous_channel),
                    onClick = { state.showActionsSheet = false; actions.viewModel.navigation.requestPreviousChannel() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            listOf(
                R.string.player_levels to { state.showLevelsSheet = true },
                R.string.player_audio_track to { state.showAudioDialog = true },
                R.string.player_subtitle_track to { state.showSubtitleDialog = true },
                R.string.player_tv_guide to { state.showGuideSheet = true },
                R.string.player_quality to { state.showQualityDialog = true },
                R.string.player_aspect_ratio to {
                    actions.viewModel.navigation.cycleResizeMode()
                    state.resizeModeToastNonce++
                },
                R.string.player_sleep_timer to { state.showSleepTimerDialog = true },
                R.string.player_view_all to { state.showChannelsSheet = true },
            ).filter { (label, _) -> playerActionVisible(label, isRemote, isFile) }.forEach { (label, action) ->
                SecondaryButton(
                    text = stringResource(label),
                    onClick = { state.showActionsSheet = false; action() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
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
