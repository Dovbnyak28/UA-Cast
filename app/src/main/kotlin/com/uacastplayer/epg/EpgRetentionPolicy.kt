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
 * The cutoff is the **start of today**, not the current moment, because [DayScheduleBuilder] shows
 * the whole of today - a programme that finished this morning is still drawn in the guide sheet.
 * Cutting at "now" would have emptied the top of that list as the day went on.
 */
object EpgRetentionPolicy {

    /** Midnight at the head of [nowMillis]'s day, in the viewer's own zone. */
    fun keepFrom(nowMillis: Long, zoneId: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

    /**
     * Whether a programme is still worth a slot.
     *
     * Kept if any part of it runs at or after [keepFromMillis], which is the same overlap rule
     * [DayScheduleBuilder] uses to decide what belongs to today - so nothing the guide would have
     * drawn is thrown away here.
     *
     * A [keepFromMillis] of 0 keeps everything, which is what a caller with no clock wants.
     */
    fun isWorthKeeping(stopMillis: Long, keepFromMillis: Long): Boolean = stopMillis > keepFromMillis
}
