package com.uacastplayer.ui.epg
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.epg.DaySchedule
import com.uacastplayer.epg.DayScheduleBuilder
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgProgramme
import com.uacastplayer.epg.ProgrammeProgress
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.components.IconHeader
import com.uacastplayer.ui.components.TrackProgress
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Title
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Today's programme lineup for a single channel, reachable from the player's quick settings and
 * from a long-press on a channel row. Kept as a bottom sheet (rather than a full screen) since
 * it's a quick lookup, not a destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgGuideSheet(channel: M3uChannel, epgData: EpgData?, nowMillis: Long, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zoneId = remember { ZoneId.systemDefault() }
    val programmes = remember(epgData, channel) {
        val epgChannel = epgData?.index?.match(channel) ?: return@remember null
        epgData.programmesByChannelId[epgChannel.id]
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding).padding(bottom = GapM)) {
            Text(
                text = channel.displayName,
                style = Title,
                color = UaTheme.palette.labelPrimary,
                maxLines = 1,
                modifier = Modifier.padding(bottom = GapM),
            )
            if (programmes.isNullOrEmpty()) {
                IconHeader(
                    icon = AppIcons.Tv,
                    title = stringResource(R.string.epg_guide_no_data),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val schedule = remember(programmes, nowMillis, zoneId) {
                    DayScheduleBuilder.build(programmes, nowMillis, zoneId)
                }
                ScheduleList(schedule = schedule, nowMillis = nowMillis, zoneId = zoneId)
            }
        }
    }
}

@Composable
private fun ScheduleList(schedule: DaySchedule, nowMillis: Long, zoneId: ZoneId) {
    val effectiveCurrentStop = schedule.upcoming.firstOrNull()?.startMillis ?: schedule.current?.stopMillis

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(schedule.past, key = { it.startMillis }) { programme ->
            ProgrammeRow(programme, state = ProgrammeRowState.PAST, zoneId = zoneId)
        }
        schedule.current?.let { current ->
            item(key = current.startMillis) {
                ProgrammeRow(
                    current,
                    state = ProgrammeRowState.CURRENT,
                    zoneId = zoneId,
                    nowMillis = nowMillis,
                    effectiveStopMillis = effectiveCurrentStop,
                )
            }
        }
        items(schedule.upcoming, key = { it.startMillis }) { programme ->
            ProgrammeRow(programme, state = ProgrammeRowState.UPCOMING, zoneId = zoneId)
        }
    }
}

private enum class ProgrammeRowState { PAST, CURRENT, UPCOMING }

@Composable
private fun ProgrammeRow(
    programme: EpgProgramme,
    state: ProgrammeRowState,
    zoneId: ZoneId,
    nowMillis: Long = 0L,
    effectiveStopMillis: Long? = null,
) {
    val timeLabel = remember(programme.startMillis, zoneId) {
        TIME_FORMATTER.format(Instant.ofEpochMilli(programme.startMillis).atZone(zoneId))
    }
    val textColor = when (state) {
        ProgrammeRowState.PAST -> UaTheme.palette.labelTertiary
        ProgrammeRowState.CURRENT -> UaTheme.palette.labelPrimary
        ProgrammeRowState.UPCOMING -> UaTheme.palette.labelPrimary
    }
    val timeColor = if (state == ProgrammeRowState.CURRENT) {
        UaTheme.palette.accentText
    } else {
        UaTheme.palette.labelTertiary
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(text = timeLabel, style = Caption, color = timeColor, modifier = Modifier.width(52.dp))
            Text(text = programme.title, style = BodyText, color = textColor, modifier = Modifier.weight(1f))
        }
        if (state == ProgrammeRowState.CURRENT && effectiveStopMillis != null) {
            TrackProgress(
                progress = ProgrammeProgress.progress(programme.startMillis, effectiveStopMillis, nowMillis),
                modifier = Modifier.padding(top = 6.dp, start = 52.dp),
            )
        }
    }
}

// locale-ok: this is a human-read-only on-screen time label (never persisted/parsed back), so
// following the device's default locale digit system is the correct behavior here, not a bug.
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
