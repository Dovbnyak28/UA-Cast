package app.zorik.player.ui.epg
import app.zorik.player.ui.theme.UaTheme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import app.zorik.player.ui.tv.TvDialogInputRegistration
import app.zorik.player.ui.tv.tvFocus
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.zorik.player.R
import app.zorik.player.epg.DayScheduleBuilder
import app.zorik.player.epg.EpgData
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.ui.components.IconHeader
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.Caption
import app.zorik.player.ui.theme.CaptionSemibold
import app.zorik.player.ui.theme.BodyText
import app.zorik.player.ui.theme.GapM
import app.zorik.player.ui.theme.ScreenHPadding
import app.zorik.player.ui.theme.Title
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
fun EpgGuideSheet(
    channel: M3uChannel,
    epgData: EpgData?,
    nowMillis: Long,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    hasError: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val windowSize = LocalWindowInfo.current.containerSize
    val compact = with(LocalDensity.current) { windowSize.height.toDp() <= COMPACT_GUIDE_HEIGHT_DP.dp }
    val zoneId = rememberEpgTimeZone()
    val today = remember(nowMillis, zoneId) {
        Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    }
    var dayOffset by rememberSaveable { mutableIntStateOf(0) }
    val selectedDate = remember(today, dayOffset) { today.plusDays(dayOffset.toLong()) }
    val programmes = remember(epgData, channel) {
        val epgChannel = epgData?.index?.match(channel) ?: return@remember null
        epgData.programmesByChannelId[epgChannel.id]
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = UaTheme.palette.surface2,
        dragHandle = { if (!compact) BottomSheetDefaults.DragHandle() }) {
        TvDialogInputRegistration()
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding).padding(bottom = GapM)) {
            GuideChannelTitle(channel.displayName, compact)
            EpgDaySelector(
                selectedDate = selectedDate,
                today = today,
                zoneId = zoneId,
                dayOffset = dayOffset,
                onPrevious = { dayOffset -= 1 },
                onNext = { dayOffset += 1 },
                onToday = { dayOffset = 0 },
                compact = compact,
            )
            val onRefresh = LocalEpgRefresh.current
            val needsRefresh = hasError || programmes.isNullOrEmpty()
            if (!programmes.isNullOrEmpty() && (isLoading || hasError)) {
                Text(
                    stringResource(if (isLoading) R.string.epg_guide_loading else R.string.epg_guide_error),
                    style = Caption,
                    color = UaTheme.palette.labelSecondary,
                )
            }
            if (!isLoading && onRefresh != null && needsRefresh) {
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.common_retry)) }
            }
            if (programmes.isNullOrEmpty()) {
                GuideMessage(epgGuideEmptyMessage(epgData != null, isLoading, hasError), compact)
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
private fun GuideChannelTitle(name: String, compact: Boolean) {
    Text(name, style = if (compact) CaptionSemibold else Title,
        color = UaTheme.palette.labelPrimary, maxLines = if (compact) 1 else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 4.dp).semantics { heading() })
}

@Composable
private fun GuideMessage(message: Int, compact: Boolean) {
    if (compact) Text(stringResource(message), style = BodyText, color = UaTheme.palette.labelSecondary)
    else IconHeader(AppIcons.Tv, stringResource(message), modifier = Modifier.fillMaxWidth())
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
    compact: Boolean,
) {
    val locale = LocalLocale.current.platformLocale
    val dateLabel = if (selectedDate == today) {
        stringResource(R.string.epg_today)
    } else {
        remember(selectedDate, locale) {
            DateTimeFormatter.ofPattern("EEE, d MMM", locale).format(selectedDate)
        }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = if (compact) 0.dp else GapM)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.tvFocus()) {
                Icon(
                    imageVector = AppIcons.ArrowBack,
                    contentDescription = stringResource(R.string.epg_previous_day),
                    tint = UaTheme.palette.accentText,
                )
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (compact) Text("$dateLabel · ${zoneId.id}", style = Caption, color = UaTheme.palette.accentText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                else {
                    Text(text = dateLabel, style = Caption, color = UaTheme.palette.accentText)
                    Text(
                        text = stringResource(R.string.epg_timezone, zoneId.id),
                        style = Caption,
                        color = UaTheme.palette.labelSecondary,
                    )
                }
            }
            IconButton(onClick = onNext, modifier = Modifier.tvFocus()) {
                Icon(
                    imageVector = AppIcons.ArrowBack,
                    contentDescription = stringResource(R.string.epg_next_day),
                    tint = UaTheme.palette.accentText,
                    modifier = Modifier.rotate(NEXT_ARROW_ROTATION_DEGREES),
                )
            }
        }
        if (dayOffset != 0) {
            TextButton(onClick = onToday, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.epg_jump_today), color = UaTheme.palette.accentText)
            }
        }
    }
}

private const val NEXT_ARROW_ROTATION_DEGREES = 180f
private const val COMPACT_GUIDE_HEIGHT_DP = 360
