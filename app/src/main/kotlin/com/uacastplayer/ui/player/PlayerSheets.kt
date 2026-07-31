package com.uacastplayer.ui.player
import com.uacastplayer.ui.theme.UaTheme

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.player.PlaybackBadgesState
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.player.SelectableTrack
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.components.SleepTimerDialog
import com.uacastplayer.ui.epg.EpgGuideSheet
import kotlin.math.roundToInt

/** The player's five independent dialogs/sheets (sleep timer, audio/subtitle track pickers,
 * quality details, EPG guide) - each `showX` flag and its dismiss callback come from
 * [PlayerScreen]'s own local state, since none of these need to be remembered here. */
// Media3 marks these as @UnstableApi, which is androidx.annotation.experimental's flavour of
// opt-in, NOT Kotlin's @RequiresOptIn - so kotlin.OptIn does not silence it (the compiler says as
// much: "'@OptIn' has no effect"). androidx.annotation.OptIn is the one that applies.
@OptIn(UnstableApi::class)
@Composable
@Suppress("LongParameterList") // glue for five independent dialog triggers, see PlayerScreen's call site
internal fun PlayerDialogs(
    viewModel: PlayerViewModel,
    uiState: PlayerUiState,
    epgState: EpgUiState,
    sleepTimer: SleepTimerState,
    currentChannel: M3uChannel?,
    showSleepTimerDialog: Boolean,
    onDismissSleepTimerDialog: () -> Unit,
    showAudioDialog: Boolean,
    onDismissAudioDialog: () -> Unit,
    showSubtitleDialog: Boolean,
    onDismissSubtitleDialog: () -> Unit,
    showQualityDialog: Boolean,
    onDismissQualityDialog: () -> Unit,
    showGuideSheet: Boolean,
    onDismissGuideSheet: () -> Unit,
) {
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            isTimerActive = sleepTimer.remainingMillis.value != null,
            onSelect = { duration ->
                sleepTimer.start(duration)
                onDismissSleepTimerDialog()
            },
            onCancelTimer = {
                sleepTimer.cancel()
                onDismissSleepTimerDialog()
            },
            onDismiss = onDismissSleepTimerDialog,
        )
    }

    if (showAudioDialog) {
        TrackPickerDialog(
            title = stringResource(R.string.player_audio_track),
            tracks = uiState.audioTracks,
            onSelect = { viewModel.selectAudioTrack(it); onDismissAudioDialog() },
            onDismiss = onDismissAudioDialog,
        )
    }

    if (showSubtitleDialog) {
        TrackPickerDialog(
            title = stringResource(R.string.player_subtitle_track),
            tracks = uiState.textTracks,
            offLabel = stringResource(R.string.player_subtitle_off),
            onSelectOff = { viewModel.clearTextTrack(); onDismissSubtitleDialog() },
            onSelect = { viewModel.selectTextTrack(it); onDismissSubtitleDialog() },
            onDismiss = onDismissSubtitleDialog,
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = onDismissQualityDialog,
            title = { Text(stringResource(R.string.player_quality)) },
            text = { QualityDetails(uiState.badges) },
            confirmButton = {
                TextButton(onClick = onDismissQualityDialog) { Text(stringResource(R.string.common_back)) }
            },
        )
    }

    if (showGuideSheet && currentChannel != null) {
        EpgGuideSheet(
            channel = currentChannel,
            epgData = epgState.data,
            nowMillis = epgState.nowMillis,
            onDismiss = onDismissGuideSheet,
        )
    }
}

@Composable
internal fun TrackPickerDialog(
    title: String,
    tracks: List<SelectableTrack>,
    offLabel: String? = null,
    onSelectOff: (() -> Unit)? = null,
    onSelect: (SelectableTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (offLabel != null && onSelectOff != null) {
                    val isOffSelected = tracks.none { it.isSelected }
                    Text(
                        text = offLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = isOffSelected, onClick = onSelectOff, role = Role.RadioButton)
                            .padding(vertical = 12.dp),
                    )
                }
                tracks.forEach { track ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = track.isSelected,
                                onClick = { onSelect(track) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            text = track.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (track.isSelected) {
                                UaTheme.palette.azure
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        trackDetailLabel(track)?.let { detail ->
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = UaTheme.palette.labelSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_back)) }
        },
    )
}

private const val UNITS_PER_KILO = 1000

/** The secondary line under a track's name in [TrackPickerDialog] - every extra field
 * [com.uacastplayer.player.PlayerViewModel.updateBadgesAndTrackLists] measured for that track
 * (codec, channel layout, sample rate, bitrate for audio; just codec for text tracks, see
 * [com.uacastplayer.player.PlaybackBadges.textCodecLabel]), joined in the same "· "-separated
 * style as [BadgesRow]. Null (not an empty string) when nothing was measured, so the caller can
 * skip rendering the line entirely instead of showing a blank one. */
@Composable
private fun trackDetailLabel(track: SelectableTrack): String? {
    val parts = buildList {
        track.codecLabel?.let(::add)
        track.channelLayout?.let { add(stringResource(it.labelRes())) }
        sampleRateLabel(track.sampleRateHz)?.let(::add)
        bitrateLabel(track.bitrateBps)?.let(::add)
    }
    return parts.joinToString(" · ").ifBlank { null }
}

/** The quality dialog's full breakdown of every field
 * [com.uacastplayer.player.PlayerViewModel.updateBadgesAndTrackLists] measured for the active
 * video and audio tracks - exact resolution (not [PlaybackBadgesState.qualityLabel]'s bucketed
 * "480p") plus codec/frame rate/bitrate for video, and a second line for audio's codec/channel
 * layout/sample rate/bitrate, since this is the one dialog with room to show everything
 * extractable rather than a single summary badge. */
@Composable
internal fun QualityDetails(badges: PlaybackBadgesState) {
    val videoParts = buildList {
        val resolution = if (badges.videoWidth != null && badges.videoHeight != null) {
            stringResource(R.string.player_resolution, badges.videoWidth, badges.videoHeight)
        } else {
            badges.qualityLabel
        }
        resolution?.let(::add)
        badges.videoCodecLabel?.let(::add)
        badges.frameRate?.let { add(stringResource(R.string.player_frame_rate, it.roundToInt())) }
        bitrateLabel(badges.videoBitrateBps)?.let(::add)
    }
    val audioParts = buildList {
        badges.audioCodecLabel?.let(::add)
        badges.channelLayout?.let { add(stringResource(it.labelRes())) }
        sampleRateLabel(badges.audioSampleRateHz)?.let(::add)
        bitrateLabel(badges.audioBitrateBps)?.let(::add)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = videoParts.joinToString(" · ").ifBlank { stringResource(R.string.player_quality_unknown) },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (audioParts.isNotEmpty()) {
            Text(
                text = stringResource(R.string.player_quality_audio_section),
                style = MaterialTheme.typography.labelMedium,
                color = UaTheme.palette.labelSecondary,
            )
            Text(text = audioParts.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun bitrateLabel(bitrateBps: Int?): String? =
    bitrateBps?.takeIf { it > 0 }?.let { stringResource(R.string.player_bitrate_kbps, it / UNITS_PER_KILO) }

@Composable
private fun sampleRateLabel(sampleRateHz: Int?): String? =
    sampleRateHz?.takeIf { it > 0 }?.let { stringResource(R.string.player_sample_rate_khz, formatKhz(it)) }

/** Sample rates are almost always a clean multiple of 1000 (48 kHz, 44.1 kHz being the one common
 * exception) - shown without a trailing ".0" for the common case instead of always fixing one
 * decimal place. */
private fun formatKhz(hz: Int): String {
    val khz = hz / UNITS_PER_KILO.toFloat()
    return if (khz == khz.toInt().toFloat()) khz.toInt().toString() else "%.1f".format(khz)
}
