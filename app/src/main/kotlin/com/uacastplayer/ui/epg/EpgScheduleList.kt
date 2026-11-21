package com.uacastplayer.ui.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.epg.DaySchedule
import com.uacastplayer.epg.EpgProgramme
import com.uacastplayer.epg.ProgrammeProgress
import com.uacastplayer.ui.components.TrackProgress
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.tv.tvFocus
import com.uacastplayer.ui.tv.tvReadingFocus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Internal rather than private so the list can be rendered without a [ModalBottomSheet] around
 * it - the sheet is a container, and what is worth asserting is what the list does with a feed. */
@Composable
internal fun ScheduleList(
    schedule: DaySchedule,
    nowMillis: Long,
    zoneId: ZoneId,
    progressNowMillis: Long? = nowMillis,
) {
    if (schedule.past.isEmpty() && schedule.current == null && schedule.upcoming.isEmpty()) {
        Text(
            stringResource(R.string.epg_guide_empty_day),
            style = BodyText,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.padding(vertical = GapM),
        )
        return
    }
    // `firstOrNull { it.startMillis > nowMillis }`, not `firstOrNull()`: since [DayScheduleBuilder]
    // stopped dropping a programme running alongside the current one, the head of `upcoming` can be
    // one that has already begun. Its start is behind `nowMillis`, which would put the progress bar
    // of the programme on air at a hard 100% for the rest of its run.
    val effectiveCurrentStop = progressNowMillis?.let { progressNow ->
        schedule.upcoming.firstOrNull { it.startMillis > progressNow }?.startMillis
            ?: schedule.current?.stopMillis
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
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
            listState.scrollToItem(schedule.past.size)
        } else {
            listState.scrollToItem(0)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        if (schedule.current != null && progressNowMillis != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { scope.launch { listState.scrollToItem(schedule.past.size) } },
                    modifier = Modifier.tvFocus()) { Text(stringResource(R.string.epg_now)) }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
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
            itemsIndexed(schedule.upcoming,
                key = { index, next -> "next-$index-${next.startMillis}" }) { _, programme ->
                ProgrammeRow(programme, state = ProgrammeRowState.UPCOMING, zoneId = zoneId)
            }
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
    val timeLabel = remember(programme.startMillis, programme.stopMillis, zoneId) {
        val start = TIME_FORMATTER.format(Instant.ofEpochMilli(programme.startMillis).atZone(zoneId))
        val end = programme.stopMillis.takeIf { it > programme.startMillis }?.let {
            TIME_FORMATTER.format(Instant.ofEpochMilli(it).atZone(zoneId))
        }
        if (end == null) start else "$start – $end"
    }
    val textColor = when (state) {
        ProgrammeRowState.PAST -> UaTheme.palette.labelSecondary
        ProgrammeRowState.CURRENT -> UaTheme.palette.labelPrimary
        ProgrammeRowState.UPCOMING -> UaTheme.palette.labelPrimary
    }
    val timeColor = if (state == ProgrammeRowState.CURRENT) {
        UaTheme.palette.accentText
    } else {
        UaTheme.palette.labelSecondary
    }

    val timeWidth = 76.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val current = state == ProgrammeRowState.CURRENT
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 480.dp && LocalDensity.current.fontScale >= 1.5f
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .background(if (current) UaTheme.colors.material.primaryContainer else UaTheme.palette.surface1,
                RoundedCornerShape(18.dp)).tvReadingFocus().padding(12.dp)) {
            if (stacked) {
                Text(text = timeLabel, style = Caption, color = timeColor)
                Text(text = programme.title, style = BodyText, color = textColor)
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Text(text = timeLabel, style = Caption, color = timeColor, modifier = Modifier.width(timeWidth))
                    Text(text = programme.title, style = BodyText, color = textColor, modifier = Modifier.weight(1f))
                }
            }
            if (state == ProgrammeRowState.CURRENT && effectiveStopMillis != null) {
                val progress = ProgrammeProgress.progress(programme.startMillis, effectiveStopMillis, nowMillis)
                TrackProgress(progress = progress,
                    modifier = Modifier.padding(top = 8.dp, start = if (stacked) 0.dp else timeWidth)
                        .progressSemantics(progress))
            }
        }
    }
}

// locale-ok: this is a human-read-only on-screen time label (never persisted/parsed back), so
// following the device's default locale digit system is the correct behavior here, not a bug.
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
