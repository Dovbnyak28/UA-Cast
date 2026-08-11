package com.uacastplayer.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Which half of a guide download survives.
 *
 * Over half of the shipped feed is television that has already been broadcast, and the cap that
 * bounds memory counts entries in document order - so keeping the past was not free, it was
 * spending the budget for the channels at the end of the file.
 */
class EpgRetentionPolicyTest {

    private val kyiv = ZoneId.of("Europe/Kyiv")

    private fun at(hour: Int, minute: Int = 0, day: Int = 11): Long =
        ZonedDateTime.of(2026, 8, day, hour, minute, 0, 0, kyiv).toInstant().toEpochMilli()

    @Test
    fun theCutoffIsMidnightInTheViewersOwnZone() {
        assertEquals(at(hour = 0), EpgRetentionPolicy.keepFrom(at(hour = 14, minute = 37), kyiv))
    }

    /**
     * Not the current moment. [DayScheduleBuilder] draws the whole of today, so a cutoff of "now"
     * would have deleted this morning's listings out from under the guide sheet as the day went on.
     */
    @Test
    fun thisMorningSurvivesEvenThoughItIsOver() {
        val keepFrom = EpgRetentionPolicy.keepFrom(at(hour = 20), kyiv)
        assertTrue(
            "a programme that ended at 09:00 is still drawn in today's schedule",
            EpgRetentionPolicy.isWorthKeeping(stopMillis = at(hour = 9), keepFromMillis = keepFrom),
        )
    }

    @Test
    fun yesterdayIsNotKept() {
        val keepFrom = EpgRetentionPolicy.keepFrom(at(hour = 20), kyiv)
        assertFalse(
            EpgRetentionPolicy.isWorthKeeping(stopMillis = at(hour = 23, day = 10), keepFromMillis = keepFrom),
        )
    }

    /** A programme running across midnight belongs to today and is kept - the same overlap rule
     * [DayScheduleBuilder] applies, so the guide never gains a gap at the day boundary. */
    @Test
    fun aProgrammeRunningThroughMidnightIsKept() {
        val keepFrom = EpgRetentionPolicy.keepFrom(at(hour = 20), kyiv)
        assertTrue(
            EpgRetentionPolicy.isWorthKeeping(stopMillis = at(hour = 1), keepFromMillis = keepFrom),
        )
    }

    /** What a caller with no clock gets: the whole feed, exactly as before this policy existed. */
    @Test
    fun aZeroCutoffKeepsEverything() {
        assertTrue(EpgRetentionPolicy.isWorthKeeping(stopMillis = 1L, keepFromMillis = 0L))
    }
}
