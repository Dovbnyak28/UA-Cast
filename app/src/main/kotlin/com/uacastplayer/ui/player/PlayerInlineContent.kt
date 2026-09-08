@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.uacastplayer.ui.player

import com.uacastplayer.player.PlayerUiState

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.guidedtour.GuidedTourKeys
import com.uacastplayer.ui.guidedtour.guidedTourTarget
import com.uacastplayer.player.PlayerCastStatusMessage
import com.uacastplayer.core.cast.CodecDisplayName
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.components.CastPeerIconGlyphSize
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.components.liveRing
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.UaTheme

private const val PLAYER_ASPECT_RATIO = 16f / 9f
private const val BUFFERING_PILL_RADIUS_DP = 999

@Composable
internal fun InlinePlayerContent(
    content: PlayerScreenContent,
    actions: PlayerScreenActions,
    transientState: PlayerScreenTransientState,
    modifier: Modifier = Modifier,
) {
    val uiState = content.uiState
    val currentChannel = uiState.currentChannel
    Column(
        modifier = modifier
            .fillMaxSize()
            .playerControlsInteraction(transientState)
            .background(UaTheme.palette.void)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState()),
    ) {
        InlinePlayerHeader(currentChannel, uiState.isCasting, content, actions, transientState)
        if (uiState.fatalError) {
            PlaybackFailureCard(
                canGoNext = uiState.canGoNext,
                onRetry = actions.viewModel::retryCurrentChannel,
                onNext = actions.viewModel.navigation::requestNext,
                onExit = actions.onExit,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = GapM),
            )
        } else {
            InlineVideoPanel(content, actions, transientState)
        }
        CastStatusBanner(uiState.castStatusMessage)
        // The terminal error card already owns Retry/Next/Back. Keeping the ordinary navigation
        // row visible at the same time produced two identically named "Next channel" actions in
        // the accessibility tree and an ambiguous TalkBack destination.
        if (!uiState.fatalError) ChannelNavigationRow(actions, uiState)
        currentChannel?.takeUnless { uiState.fatalError }?.let { channel ->
            ChannelInfoCard(
                channel = channel,
                badges = uiState.badges,
                iconRefreshKey = content.iconRefreshKey,
                resolveIcon = actions.resolveIcon,
            )
        }
        QuickSettingsRow(
            showAudio = !uiState.fatalError && !remotePlaybackOwnsControls(uiState, content.dlnaState),
            onAudioClick = { transientState.showAudioDialog = true },
            onGuideClick = { transientState.showGuideSheet = true },
            onMoreClick = { transientState.showActionsSheet = true },
        )
        if (uiState.nextChannelsPreview.isNotEmpty()) {
            NextChannelsRail(
                channels = uiState.nextChannelsPreview,
                iconRefreshKey = content.iconRefreshKey,
                resolveIcon = actions.resolveIcon,
                onSelect = { actions.viewModel.navigation.requestSwitch(it.index) },
                onViewAll = { transientState.showChannelsSheet = true },
            )
        }
        if (!uiState.fatalError) {
            SecondaryButton(
                text = stringResource(R.string.player_back_to_channels),
                onClick = actions.onExit,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = GapL),
            )
        }
    }
}

@Composable
private fun InlinePlayerHeader(
    currentChannel: M3uChannel?,
    isCasting: Boolean,
    content: PlayerScreenContent,
    actions: PlayerScreenActions,
    transientState: PlayerScreenTransientState,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallRoundIconButton(
            icon = AppIcons.ArrowBack,
            onClick = actions.onExit,
            contentDescription = stringResource(R.string.common_back),
        )
        Text(
            text = currentChannel?.displayName ?: stringResource(R.string.app_name),
            style = Title,
            color = UaTheme.palette.labelPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        currentChannel?.let { channel ->
            SmallRoundIconButton(
                icon = AppIcons.Favorites,
                onClick = { actions.onToggleFavorite(channel) },
                contentDescription = stringResource(
                    if (actions.isFavorite(channel)) R.string.channels_channel_remove_favorite
                    else R.string.channels_channel_add_favorite,
                ),
                tint = if (actions.isFavorite(channel)) {
                    UaTheme.palette.azure
                } else {
                    UaTheme.palette.labelPrimary
                },
            )
        }
        val dlnaConnected = content.dlnaState.connectedDevice != null
        SmallRoundIconButton(
            icon = AppIcons.CastToTv,
            onClick = { transientState.showDevicePicker = true },
            contentDescription = stringResource(
                R.string.player_devices,
            ),
            tint = if (dlnaConnected || isCasting) UaTheme.palette.azure else UaTheme.palette.labelPrimary,
            iconSize = CastPeerIconGlyphSize,
            modifier = Modifier.guidedTourTarget(GuidedTourKeys.CAST_BUTTON)
                .liveRing(active = dlnaConnected || isCasting, color = UaTheme.palette.azure),
        )
    }
}

@Composable
private fun InlineVideoPanel(
    content: PlayerScreenContent,
    actions: PlayerScreenActions,
    transientState: PlayerScreenTransientState,
) {
    val uiState = content.uiState
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHPadding)
            .aspectRatio(PLAYER_ASPECT_RATIO)
            .clip(RoundedCornerShape(RadiusCard))
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(R.string.player_toggle_controls),
                onClick = { transientState.controlsVisible = !transientState.controlsVisible },
            ),
    ) {
        VideoSurface(
            viewModel = actions.viewModel,
            resizeMode = content.videoResizeMode,
            modifier = Modifier.fillMaxSize(),
        )
        when {
            uiState.autoSkipRecovery != null -> AutoSkipRecoveryIndicator(
                state = uiState.autoSkipRecovery,
                onCancel = actions.viewModel::cancelAutoSkipRecovery,
                modifier = Modifier.align(Alignment.Center),
            )
            uiState.isRecoveringPlayback -> RecoveringPlaybackIndicator(
                attempt = uiState.stallRecoveryAttempt,
                onPickAnotherChannel = actions.onExit,
                modifier = Modifier.align(Alignment.Center),
            )
            uiState.isBuffering && !uiState.fatalError -> Text(
                text = stringResource(R.string.player_buffering),
                color = Color.White,
                style = Caption,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(BUFFERING_PILL_RADIUS_DP.dp))
                    .background(UaTheme.palette.scrimBackground)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (transientState.showResizeModeToast) {
            ResizeModeToast(uiState.resizeMode, modifier = Modifier.align(Alignment.Center))
        }
        if (transientState.controlsVisible && !uiState.fatalError) {
            InlineVideoControls(
                wantsToPlay = uiState.wantsToPlay,
                canControlPlayback = uiState.canControlPlayback,
                onPlayPause = actions.viewModel::togglePlayback,
                onToggleFullscreen = { actions.onFullscreenChanged(true) },
            )
        }
    }
}

@Composable
private fun CastStatusBanner(status: PlayerCastStatusMessage?) {
    val message = when (status) {
        null -> null
        is PlayerCastStatusMessage.IncompatibleVideo -> stringResource(
            R.string.cast_incompatible_video_message,
            CodecDisplayName.of(status.codec),
        )
        PlayerCastStatusMessage.Recovering -> stringResource(R.string.cast_recovering_message)
        PlayerCastStatusMessage.ProxyUnavailableIpv4Only ->
            stringResource(R.string.cast_proxy_ipv4_unavailable_message)
        is PlayerCastStatusMessage.LikelyIncompatibleVideo -> stringResource(
            R.string.cast_likely_incompatible_video_message,
            CodecDisplayName.of(status.codec),
        )
        is PlayerCastStatusMessage.LikelyIncompatibleAudio -> stringResource(
            R.string.cast_likely_incompatible_audio_message,
            CodecDisplayName.of(status.codec),
        )
        PlayerCastStatusMessage.ReceiverLoadFailed -> stringResource(R.string.cast_receiver_load_failed_message)
    }
    message?.let {
        CastIncompatibilityBanner(
            message = it,
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 4.dp),
        )
    }
}

@Composable
private fun ChannelNavigationRow(actions: PlayerScreenActions, uiState: PlayerUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = GapM),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PillButton(
            icon = AppIcons.SkipPrevious,
            label = stringResource(R.string.player_previous),
            onClick = actions.viewModel.navigation::requestPrevious,
            enabled = uiState.canGoPrevious,
            modifier = Modifier.weight(1f),
        )
        PillButton(
            icon = AppIcons.SkipNext,
            label = stringResource(R.string.player_next),
            onClick = actions.viewModel.navigation::requestNext,
            enabled = uiState.canGoNext,
            modifier = Modifier.weight(1f),
            iconTrailing = true,
        )
    }
}
