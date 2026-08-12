package com.uacastplayer.epg

import java.time.Instant
import java.time.ZoneId

/**
 * Which part of a downloaded guide is worth keeping in memory.
 *
 * Measured against the feed this app ships with (`epg.it999.ru/epg2.xml.gz`, 494MB of XML) on a
 * real 2863-channel playlist: 793,417 programmes, of which **388,863 had already finished before
 * today began**. Over half of every guide download was last week's television, held in RAM for the
 * lifetime of the app and then written into the snapshot cache.
 *
 * That mattered because the cap that stops a feed exhausting memory is a *count*, applied in
 * document order - so those spent programmes were not merely wasted, they were evicting the future
 * of every channel that appears late in the file. Dropping them is what makes the cap reach.
 *
 * The near cutoff is the **start of today**, not the current moment, because [DayScheduleBuilder]
 * shows the whole of today - a programme that finished this morning is still drawn in the guide
 * sheet. Cutting at "now" would have emptied the top of that list as the day went on.
 *
 * The far cutoff exists for the same reason as the near one, and was found the same way: a
 * diagnostics report from a 311-channel playlist whose guide carried 4052 channels and hit the
 * 400,000 cap exactly. Dropping the past had not been enough, because nothing dropped the far
 * future - feeds carry about eight days, and the whole of the app can show exactly one. There are
 * two consumers of programme data, [ProgrammeLookup] (now/next) and [DayScheduleBuilder] (today);
 * no screen anywhere offers tomorrow, let alone day eight. Seven eighths of what was evicting other
 * channels from the guide could not be looked at by anybody.
 */
object EpgRetentionPolicy {

    /**
     * How many days of guide to keep, counting today as the first.
     *
     * Three rather than one, because the guide has to outlive its own refresh: [EpgRefreshPolicy]
     * only re-downloads on an unmetered network, and the report this was measured from said
     * "mobile, metered". Two spare days is what a phone that does not see Wi-Fi over a weekend
     * needs to still have a guide on Monday.
     *
     * Three rather than more, because that is what makes the cap stop being reached. Measured on
     * the shipped feed: 346,837 programmes over about eight days, so roughly 43,000 a day - three
     * days is about 130,000 against a [XmlTvParser.MAX_PROGRAMMES] of 400,000. The margin is the
     * point. A feed that fits never gets truncated, and truncation is the failure that matters
     * here: being count-based and in document order, it does not thin everyone's guide evenly, it
     * gives the channels at the end of the file nothing at all.
     */
    const val DAYS_KEPT = 3

    /** Midnight at the head of [nowMillis]'s day, in the viewer's own zone. */
    fun keepFrom(nowMillis: Long, zoneId: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

    /**
     * Midnight at the end of the last day worth keeping, in the viewer's own zone.
     *
     * Computed through [java.time.LocalDate.plusDays] rather than `keepFrom + n * 24h`, for the
     * reason [DayScheduleBuilder] documents at length: a day is not 24 hours in a zone that changes
     * its clocks, and Europe/Kyiv changes its clocks twice a year.
     */
    fun keepUntil(nowMillis: Long, zoneId: ZoneId, daysKept: Int = DAYS_KEPT): Long =
        Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .toLocalDate()
            .plusDays(daysKept.toLong())
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

    /**
     * Whether a programme is still worth a slot.
     *
     * Kept if any part of it runs inside the window: it must not have finished before
     * [keepFromMillis], and it must start before [keepUntilMillis]. The near end is the same
     * overlap rule [DayScheduleBuilder] uses to decide what belongs to today, so nothing the guide
     * would have drawn is thrown away here; the far end keeps a programme that begins on the last
     * kept evening and runs past midnight, which is the one the guide draws at the bottom.
     *
     * A [keepFromMillis] of 0 with a [keepUntilMillis] of [Long.MAX_VALUE] keeps everything, which
     * is what a caller with no clock wants.
     */
    fun isWorthKeeping(
        startMillis: Long,
        stopMillis: Long,
        keepFromMillis: Long,
        keepUntilMillis: Long,
    ): Boolean = stopMillis > keepFromMillis && startMillis < keepUntilMillis
}
