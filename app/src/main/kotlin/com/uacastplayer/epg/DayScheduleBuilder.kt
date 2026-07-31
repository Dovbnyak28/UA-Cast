package com.uacastplayer.epg

import java.time.Instant
import java.time.ZoneId

/** A single channel's programmes for "today" (the local calendar day containing [nowMillis]),
 * split into what already aired, what's airing now, and what's still to come. */
data class DaySchedule(
    val past: List<EpgProgramme>,
    val current: EpgProgramme?,
    val upcoming: List<EpgProgramme>,
)

/**
 * Builds a single day's guide for one channel from its full programme list. Kept separate from
 * [ProgrammeLookup] (which only cares about the current/next pair for a status badge) because the
 * guide sheet needs the whole day's lineup, bucketed by "now" vs "later today" per the local
 * calendar day - not the raw EPG feed's arbitrary time window.
 */
object DayScheduleBuilder {

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** [programmes] need not be sorted or pre-filtered to a single channel/day - both are done here. */
    fun build(programmes: List<EpgProgramme>, nowMillis: Long, zoneId: ZoneId): DaySchedule {
        val dayStartMillis = Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val dayEndMillis = dayStartMillis + DAY_MILLIS

        // A programme belongs to "today" if any part of its run overlaps today's window - this
        // deliberately keeps a programme that started yesterday and airs past midnight, and one
        // that starts before midnight tonight, so the guide never has a gap at the day boundary.
        val todays = programmes
            .filter { it.stopMillis > dayStartMillis && it.startMillis < dayEndMillis }
            .sortedBy { it.startMillis }

        val current = todays.firstOrNull { it.startMillis <= nowMillis && it.stopMillis > nowMillis }
        val past = todays.filter { it.stopMillis <= nowMillis }
        val upcoming = todays.filter { it.startMillis > nowMillis }

        return DaySchedule(past, current, upcoming)
    }
}
