package com.uacastplayer.ui.epg
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLocale
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
import java.time.LocalDate
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
    val today = remember(nowMillis, zoneId) {
        Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    }
    var dayOffset by rememberSaveable { mutableIntStateOf(0) }
    val selectedDate = remember(today, dayOffset) { today.plusDays(dayOffset.toLong()) }
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
                modifier = Modifier.padding(bottom = 4.dp),
            )
            EpgDaySelector(
                selectedDate = selectedDate,
                today = today,
                zoneId = zoneId,
                dayOffset = dayOffset,
                onPrevious = { dayOffset -= 1 },
                onNext = { dayOffset += 1 },
                onToday = { dayOffset = 0 },
            )
            if (programmes.isNullOrEmpty()) {
                IconHeader(
                    icon = AppIcons.Tv,
                    title = stringResource(R.string.epg_guide_no_data),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val schedule = remember(programmes, nowMillis, zoneId, selectedDate) {
                    DayScheduleBuilder.build(programmes, nowMillis, zoneId, selectedDate)
                }
                ScheduleList(
                    schedule = schedule,
                    nowMillis = nowMillis,
                    zoneId = zoneId,
                    progressNowMillis = nowMillis.takeIf { selectedDate == today },
                )
            }
        }
    }
}

@Composable
private fun EpgDaySelector(
    selectedDate: LocalDate,
    today: LocalDate,
    zoneId: ZoneId,
    dayOffset: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val dateLabel = if (selectedDate == today) {
        stringResource(R.string.epg_today)
    } else {
        remember(selectedDate, locale) {
            DateTimeFormatter.ofPattern("EEE, d MMM", locale).format(selectedDate)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = GapM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = AppIcons.ArrowBack,
                contentDescription = stringResource(R.string.epg_previous_day),
                tint = UaTheme.palette.accentText,
            )
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = dateLabel, style = Caption, color = UaTheme.palette.accentText)
            Text(
                text = stringResource(R.string.epg_timezone, zoneId.id),
                style = Caption,
                color = UaTheme.palette.labelTertiary,
            )
        }
        if (dayOffset != 0) {
            TextButton(onClick = onToday) {
                Text(text = stringResource(R.string.epg_jump_today), color = UaTheme.palette.accentText)
            }
        }
        IconButton(onClick = onNext) {
            Icon(
                imageVector = AppIcons.ArrowBack,
                contentDescription = stringResource(R.string.epg_next_day),
                tint = UaTheme.palette.accentText,
                modifier = Modifier.rotate(NEXT_ARROW_ROTATION_DEGREES),
            )
        }
    }
}

/** Internal rather than private so the list can be rendered without a [ModalBottomSheet] around
 * it - the sheet is a container, and what is worth asserting is what the list does with a feed. */
@Composable
internal fun ScheduleList(
    schedule: DaySchedule,
    nowMillis: Long,
    zoneId: ZoneId,
    progressNowMillis: Long? = nowMillis,
) {
    // `firstOrNull { it.startMillis > nowMillis }`, not `firstOrNull()`: since [DayScheduleBuilder]
    // stopped dropping a programme running alongside the current one, the head of `upcoming` can be
    // one that has already begun. Its start is behind `nowMillis`, which would put the progress bar
    // of the programme on air at a hard 100% for the rest of its run.
    val effectiveCurrentStop = progressNowMillis?.let { progressNow ->
        schedule.upcoming.firstOrNull { it.startMillis > progressNow }?.startMillis
            ?: schedule.current?.stopMillis
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // A single list state survives while the user switches between dates. Reset it when the
    // bucket contents change, otherwise a guide scrolled near the end of today can open tomorrow
    // halfway down (or on an empty tail) and look as if entries are missing.
    LaunchedEffect(
        schedule.past.size,
        schedule.past.firstOrNull()?.startMillis,
        schedule.past.lastOrNull()?.startMillis,
        schedule.current?.startMillis,
        schedule.upcoming.size,
        schedule.upcoming.firstOrNull()?.startMillis,
        schedule.upcoming.lastOrNull()?.startMillis,
    ) {
        if (schedule.current != null) {
            listState.animateScrollToItem(schedule.past.size)
        } else {
            listState.scrollToItem(0)
        }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        // Keyed on the position as well as the start time, because a start time is not unique and a
        // `LazyColumn` requires that its keys are: a repeated key is an IllegalArgumentException
        // thrown out of composition, not a duplicated row. Two `<programme>` entries at the same
        // start on one channel are ordinary in an aggregated feed, and nothing between the XML and
        // this list removes them - so opening the guide for such a channel crashed. The start time
        // stays in the key so that a row keeps its identity as the clock moves it from one bucket
        // to the next, which is what the key was for.
        itemsIndexed(schedule.past, key = { index, past -> "past-$index-${past.startMillis}" }) { _, programme ->
            ProgrammeRow(programme, state = ProgrammeRowState.PAST, zoneId = zoneId)
        }
        schedule.current?.let { current ->
            item(key = "current-${current.startMillis}") {
                ProgrammeRow(
                    current,
                    state = ProgrammeRowState.CURRENT,
                    zoneId = zoneId,
                    nowMillis = progressNowMillis ?: current.startMillis,
                    effectiveStopMillis = effectiveCurrentStop,
                )
            }
        }
        itemsIndexed(schedule.upcoming, key = { index, next -> "next-$index-${next.startMillis}" }) { _, programme ->
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
private const val NEXT_ARROW_ROTATION_DEGREES = 180f
