@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package app.zorik.player.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zorik.player.R
import app.zorik.player.media.LocalVideo
import app.zorik.player.player.PlaybackBadgesState
import app.zorik.player.player.PlayerUiState
import app.zorik.player.player.PlayerViewModel
import app.zorik.player.ui.theme.CaptionSemibold
import app.zorik.player.ui.theme.GapM
import app.zorik.player.ui.theme.ScreenHPadding
import app.zorik.player.ui.theme.UaTheme

@Composable
internal fun FileInfoCard(video: LocalVideo, badges: PlaybackBadgesState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 8.dp)) {
        Text(video.displayName, style = CaptionSemibold, color = UaTheme.palette.labelPrimary,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        BadgesRow(badges, Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun InlinePlaybackTimeline(content: PlayerScreenContent, actions: PlayerScreenActions) {
    val state = content.uiState
    if (state.fatalError || remotePlaybackOwnsControls(state, content.dlnaState)) return
    if (state.currentChannel?.localVideo != null || state.canSeek) {
        FilePlaybackSeekBar(actions.viewModel, state,
            Modifier.padding(horizontal = ScreenHPadding, vertical = GapM))
    }
}

@Composable
internal fun FilePlaybackSeekBar(viewModel: PlayerViewModel, state: PlayerUiState, modifier: Modifier = Modifier) {
    val timeline by rememberFilePlaybackTimeline(viewModel.player, state.currentChannel?.streamUrl, state.isPlaying)
    FilePlaybackProgress(timeline, state.canSeek, viewModel.navigation::seekTo, modifier,
        onRelativeSeek = viewModel.navigation::seekBy)
}

@Composable
internal fun FilePlaybackProgress(
    timeline: FilePlaybackTimeline, enabled: Boolean, onSeek: (Long) -> Unit, modifier: Modifier = Modifier,
    onRelativeSeek: (Long) -> Unit = { delta -> onSeek(timeline.relativePosition(delta)) },
) {
    var dragged by remember(timeline.duration, enabled) { mutableStateOf<Float?>(null) }
    val shown = dragged ?: timeline.fraction
    val available = enabled && timeline.duration > 0
    val position = dragged?.let(timeline::positionAt) ?: timeline.position
    val seekLabel = stringResource(R.string.local_video_seek)
    val compact = LocalCompactPlayerControls.current
    val durationText = if (timeline.duration > 0) filePlaybackTime(timeline.duration) else "--:--"
    if (compact && available) {
        // Phone landscape: one 48dp row instead of three, so the panel leaves most of the video
        // visible. Same controls, same order a viewer reads them in.
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(filePlaybackTime(position), style = CaptionSemibold, color = UaTheme.palette.labelPrimary)
            FileSeekButton(stringResource(R.string.local_video_rewind_ten), true) {
                onRelativeSeek(-FILE_SEEK_STEP_MILLIS)
            }
            FileSeekSlider(shown, available, seekLabel, position, timeline, onSeek,
                onDrag = { dragged = it }, onDragEnd = { dragged = null }, dragged = { dragged },
                modifier = Modifier.weight(1f))
            FileSeekButton(stringResource(R.string.local_video_forward_ten), true) {
                onRelativeSeek(FILE_SEEK_STEP_MILLIS)
            }
            Text(durationText, style = CaptionSemibold, color = UaTheme.palette.labelSecondary)
        }
        return
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(filePlaybackTime(position), style = CaptionSemibold, color = UaTheme.palette.labelPrimary)
            Text(durationText, style = CaptionSemibold, color = UaTheme.palette.labelSecondary)
        }
        if (available) FileSeekSlider(shown, available, seekLabel, position, timeline, onSeek,
            onDrag = { dragged = it }, onDragEnd = { dragged = null }, dragged = { dragged },
            modifier = Modifier.fillMaxWidth())
        if (available) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FileRelativeSeekControls(available, onRelativeSeek)
            }
        }
    }
}

@Composable
@Suppress("LongParameterList") // One slider shared by the stacked and the single-row layout.
private fun FileSeekSlider(
    shown: Float,
    available: Boolean,
    seekLabel: String,
    position: Long,
    timeline: FilePlaybackTimeline,
    onSeek: (Long) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    dragged: () -> Float?,
    modifier: Modifier,
) {
    Slider(
        value = shown, onValueChange = onDrag, enabled = available,
        onValueChangeFinished = { dragged()?.let { if (available) onSeek(timeline.positionAt(it)) }; onDragEnd() },
        colors = SliderDefaults.colors(thumbColor = UaTheme.palette.azure, activeTrackColor = UaTheme.palette.azure,
            inactiveTrackColor = UaTheme.palette.overlayHighlight),
        modifier = modifier.heightIn(min = 48.dp).semantics {
            contentDescription = seekLabel
            stateDescription = "${filePlaybackTime(position)} / ${filePlaybackTime(timeline.duration)}"
            setProgress { requested ->
                if (available && requested.isFinite()) {
                    onDragEnd()
                    onSeek(timeline.positionAt(requested))
                    true
                } else false
            }
        },
    )
}

@Composable
private fun FileRelativeSeekControls(enabled: Boolean, onRelativeSeek: (Long) -> Unit) {
    FileSeekButton(stringResource(R.string.local_video_rewind_ten), enabled) {
        onRelativeSeek(-FILE_SEEK_STEP_MILLIS)
    }
    FileSeekButton(stringResource(R.string.local_video_forward_ten), enabled) {
        onRelativeSeek(FILE_SEEK_STEP_MILLIS)
    }
}

@Composable
private fun FileSeekButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}

private const val FILE_SEEK_STEP_MILLIS = 10_000L
