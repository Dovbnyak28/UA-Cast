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

    /** [programmes] need not be sorted or pre-filtered to a single channel/day - both are done here. */
    fun build(programmes: List<EpgProgramme>, nowMillis: Long, zoneId: ZoneId): DaySchedule {
        // Both ends come from the calendar. The end used to be `start + 24h`, which is only the
        // same thing in a zone that never changes its clocks - and the tests all used UTC, which is
        // one. In Europe/Kyiv the October Sunday is 25 hours long, so "start + 24h" fell at 23:00
        // and silently dropped the last hour of that evening's listings; the March Sunday is 23
        // hours long, so it overshot to 01:00 and pulled the next morning into today instead.
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val dayStartMillis = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEndMillis = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        // A programme belongs to "today" if any part of its run overlaps today's window - this
        // deliberately keeps a programme that started yesterday and airs past midnight, and one
        // that starts before midnight tonight, so the guide never has a gap at the day boundary.
        val todays = programmes
            .filter { it.stopMillis > dayStartMillis && it.startMillis < dayEndMillis }
            .sortedBy { it.startMillis }

        // Split once, rather than filtered three times with three independent predicates. Those
        // three - finished, "the first one airing", starts later - only partition the day while at
        // most one programme is airing at a time, and overlapping listings are routine: the very
        // reason [ProgrammeLookup] prefers the next start over the declared stop is that feeds
        // disagree with themselves about where one programme ends. The second of two overlapping
        // programmes matched none of the three and was dropped from the guide silently.
        val (past, unfinished) = todays.partition { it.stopMillis <= nowMillis }
        // The list is sorted by start, so the first unfinished programme that has already begun is
        // the one on air. Anything else still to finish is upcoming - including a programme running
        // alongside this one, which now reads as "starting next" rather than as nothing at all.
        val currentIndex = unfinished.indexOfFirst { it.startMillis <= nowMillis }
        val current = unfinished.getOrNull(currentIndex)
        val upcoming = unfinished.filterIndexed { index, _ -> index != currentIndex }

        return DaySchedule(past, current, upcoming)
    }
}
