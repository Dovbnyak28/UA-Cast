package com.uacastplayer.ui.player
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.uacastplayer.R
import com.uacastplayer.epg.EpgLookup
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.components.ChannelIcon
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.ChannelLogoRadius
import com.uacastplayer.ui.theme.MiniPlayerBarHeight
import com.uacastplayer.ui.theme.raisedSurface
import java.io.File

private const val VIDEO_ASPECT_RATIO = 16f / 9f
private val BufferingIndicatorSize = 20.dp
private val BufferingIndicatorStrokeWidth = 2.dp

/**
 * The 64dp collapsed player bar - shown above the bottom nav on every tab while a channel is
 * loaded and the player isn't fullscreen (see
 * [PlayerContainerStateMachine][com.uacastplayer.player.PlayerContainerStateMachine]).
 * Tapping the bar itself expands back to fullscreen; the close button stops playback outright.
 *
 * Shares the same [viewModel] (and thus the same [androidx.media3.exoplayer.ExoPlayer] instance)
 * PlayerScreen uses - re-parenting the video output to a new [VideoSurface] here is a normal,
 * cheap operation for ExoPlayer, not a full player restart. While casting, the local player isn't
 * the one actually playing (see `LocalPlaybackPolicy`), so this shows the channel's logo instead
 * of a frozen/black video surface, plus a small "on TV" badge.
 */
@OptIn(markerClass = [UnstableApi::class])
@Composable
fun MiniPlayerBar(
    viewModel: PlayerViewModel,
    resolveIcon: suspend (M3uChannel) -> File?,
    epgState: EpgUiState,
    iconRefreshKey: Any,
    onTap: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val channel = uiState.currentChannel ?: return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerBarHeight)
            .raisedSurface(RoundedCornerShape(16.dp), UaTheme.palette.surface1, shadow = true)
            .clickable(onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(VIDEO_ASPECT_RATIO)
                .clip(RoundedCornerShape(ChannelLogoRadius)),
            contentAlignment = Alignment.Center,
        ) {
            if (uiState.isCasting) {
                ChannelIcon(channel = channel, resolveIcon = resolveIcon, refreshKey = iconRefreshKey)
            } else {
                VideoSurface(
                    viewModel = viewModel,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    modifier = Modifier.fillMaxHeight().aspectRatio(VIDEO_ASPECT_RATIO),
                )
            }
            if (uiState.isBuffering && !uiState.isCasting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(BufferingIndicatorSize),
                    strokeWidth = BufferingIndicatorStrokeWidth,
                    color = Color.White,
                )
            }
        }

        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.displayName,
                    style = BodyText,
                    color = UaTheme.palette.labelPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (uiState.isCasting) {
                    Text(
                        text = stringResource(R.string.player_mini_on_tv_badge),
                        style = Caption,
                        color = UaTheme.palette.accentText,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            val programme = remember(channel.streamUrl, epgState.data, epgState.nowMillis) {
                epgState.data?.let { EpgLookup.currentAndNext(it, channel, epgState.nowMillis) }
            }
            programme?.current?.title?.let { title ->
                Text(text = title, style = Caption, color = UaTheme.palette.labelSecondary, maxLines = 1)
            }
        }

        if (!uiState.isCasting) {
            val playPauseLabelRes = if (uiState.isPlaying) R.string.player_pause else R.string.player_play
            SmallRoundIconButton(
                icon = if (uiState.isPlaying) AppIcons.Pause else AppIcons.Play,
                onClick = {
                    if (viewModel.player.isPlaying) viewModel.player.pause() else viewModel.player.play()
                },
                contentDescription = stringResource(playPauseLabelRes),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        SmallRoundIconButton(
            icon = Icons.Filled.Close,
            onClick = onClose,
            contentDescription = stringResource(R.string.player_mini_close),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
