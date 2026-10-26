@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.uacastplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.media.LocalVideo
import com.uacastplayer.player.PlaybackBadgesState
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.CardPadding
import com.uacastplayer.ui.theme.CaptionSemibold
import com.uacastplayer.ui.theme.DisplayName
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.raisedSurface

@Composable
internal fun FileInfoCard(video: LocalVideo, badges: PlaybackBadgesState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding)
        .raisedSurface(RoundedCornerShape(RadiusCard), UaTheme.palette.surface1)
        .padding(CardPadding), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).raisedSurface(RoundedCornerShape(16.dp), UaTheme.palette.surface2),
            contentAlignment = Alignment.Center) {
            Icon(AppIcons.Play, null, tint = UaTheme.palette.azure, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.padding(start = GapM).weight(1f)) {
            Text(stringResource(R.string.local_video_label), style = CaptionSemibold,
                color = UaTheme.palette.accentText)
            Text(video.displayName, style = DisplayName, color = UaTheme.palette.labelPrimary,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            BadgesRow(badges, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
internal fun FilePlaybackSeekBar(viewModel: PlayerViewModel, state: PlayerUiState, modifier: Modifier = Modifier) {
    val timeline by rememberFilePlaybackTimeline(viewModel.player, state.currentChannel?.streamUrl, state.isPlaying)
    FilePlaybackProgress(timeline, state.canSeek, viewModel.navigation::seekTo, modifier)
}

@Composable
internal fun FilePlaybackProgress(
    timeline: FilePlaybackTimeline, enabled: Boolean, onSeek: (Long) -> Unit, modifier: Modifier = Modifier,
) {
    var dragged by remember(timeline.duration) { mutableStateOf<Float?>(null) }
    val shown = dragged ?: timeline.fraction
    val available = enabled && timeline.duration > 0
    val position = dragged?.let(timeline::positionAt) ?: timeline.position
    val seekLabel = stringResource(R.string.local_video_seek)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(filePlaybackTime(position), style = CaptionSemibold, color = UaTheme.palette.labelPrimary)
            Text(if (timeline.duration > 0) filePlaybackTime(timeline.duration) else "--:--",
                style = CaptionSemibold, color = UaTheme.palette.labelSecondary)
        }
        Slider(
            value = shown, onValueChange = { dragged = it }, enabled = available,
            onValueChangeFinished = { dragged?.let { if (available) onSeek(timeline.positionAt(it)) }; dragged = null },
            colors = SliderDefaults.colors(thumbColor = UaTheme.palette.azure, activeTrackColor = UaTheme.palette.azure,
                inactiveTrackColor = UaTheme.palette.overlayHighlight),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                contentDescription = seekLabel
                stateDescription = "${filePlaybackTime(position)} / ${filePlaybackTime(timeline.duration)}"
                setProgress { requested ->
                    if (available && requested.isFinite()) {
                        dragged = null
                        onSeek(timeline.positionAt(requested))
                        true
                    } else false
                }
            },
        )
    }
}
