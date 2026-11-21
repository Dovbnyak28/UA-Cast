package com.uacastplayer.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Which part of a guide download survives.
 *
 * Over half of the shipped feed is television that has already been broadcast, and most of the rest
 * is television no screen in this app can reach. The cap that bounds memory counts entries in
 * document order, so keeping either was not free - it was spending the budget for the channels at
 * the end of the file.
 */
class EpgRetentionPolicyTest {

    private val kyiv = ZoneId.of("Europe/Kyiv")

    private fun at(hour: Int, minute: Int = 0, day: Int = 11): Long =
        ZonedDateTime.of(2026, 8, day, hour, minute, 0, 0, kyiv).toInstant().toEpochMilli()

    /** Convenience for the cases that only care about one end of the window. */
    private fun keeps(
        startMillis: Long,
        stopMillis: Long,
        keepFromMillis: Long = 0L,
        keepUntilMillis: Long = Long.MAX_VALUE,
    ) = EpgRetentionPolicy.isWorthKeeping(startMillis, stopMillis, keepFromMillis, keepUntilMillis)

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
            keeps(startMillis = at(hour = 8), stopMillis = at(hour = 9), keepFromMillis = keepFrom),
        )
    }

    @Test
    fun yesterdayIsNotKept() {
        val keepFrom = EpgRetentionPolicy.keepFrom(at(hour = 20), kyiv)
        assertFalse(
            keeps(
                startMillis = at(hour = 22, day = 10),
                stopMillis = at(hour = 23, day = 10),
                keepFromMillis = keepFrom,
            ),
        )
    }

    /** A programme running across midnight belongs to today and is kept - the same overlap rule
     * [DayScheduleBuilder] applies, so the guide never gains a gap at the day boundary. */
    @Test
    fun aProgrammeRunningThroughMidnightIsKept() {
        val keepFrom = EpgRetentionPolicy.keepFrom(at(hour = 20), kyiv)
        assertTrue(
            keeps(startMillis = at(hour = 23, day = 10), stopMillis = at(hour = 1), keepFromMillis = keepFrom),
        )
    }

    /** What a caller with no clock gets: the whole feed, exactly as before this policy existed. */
    @Test
    fun anOpenWindowKeepsEverything() {
        assertTrue(keeps(startMillis = 0L, stopMillis = 1L))
    }

    /**
     * The far end, counted in calendar days rather than multiples of 24 hours.
     *
     * [DAYS_KEPT][EpgRetentionPolicy.DAYS_KEPT] of 3 starting on the 11th means the window closes
     * at midnight opening the 14th - so the 13th is kept whole and the 14th is not there at all.
     */
    @Test
    fun theWindowClosesAtMidnightThreeDaysOn() {
        assertEquals(
            ZonedDateTime.of(2026, 8, 14, 0, 0, 0, 0, kyiv).toInstant().toEpochMilli(),
            EpgRetentionPolicy.keepUntil(at(hour = 14, minute = 37), kyiv),
        )
    }

    /**
     * The reason this end exists at all, taken from a real diagnostics report: a 311-channel
     * playlist whose guide carried 4052 channels and hit the 400,000 cap exactly, so channels late
     * in the file had no listings. Feeds carry about eight days; the whole of this app shows one
     * ([DayScheduleBuilder]) plus the programme after it ([ProgrammeLookup]). Day eight was evicting
     * other people's channels from a guide nobody could open it in.
     */
    @Test
    fun aWeekAheadIsNotKept() {
        val now = at(hour = 20)
        assertFalse(
            keeps(
                startMillis = at(hour = 20, day = 18),
                stopMillis = at(hour = 21, day = 18),
                keepFromMillis = EpgRetentionPolicy.keepFrom(now, kyiv),
                keepUntilMillis = EpgRetentionPolicy.keepUntil(now, kyiv),
            ),
        )
    }

    /** Two spare days is the point of keeping three: [EpgRefreshPolicy] only re-downloads on an
     * unmetered network, and the report this came from said "mobile, metered". */
    @Test
    fun theDayAfterTomorrowIsKept() {
        val now = at(hour = 20)
        assertTrue(
            keeps(
                startMillis = at(hour = 20, day = 13),
                stopMillis = at(hour = 21, day = 13),
                keepFromMillis = EpgRetentionPolicy.keepFrom(now, kyiv),
                keepUntilMillis = EpgRetentionPolicy.keepUntil(now, kyiv),
            ),
        )
    }

    /** The last evening's late film starts inside the window and ends outside it. Judged on its
     * start, so the guide's last day is not missing its bottom row. */
    @Test
    fun aProgrammeStartingOnTheLastKeptEveningIsKeptEvenIfItEndsAfterTheWindow() {
        val now = at(hour = 20)
        assertTrue(
            keeps(
                startMillis = at(hour = 23, day = 13),
                stopMillis = at(hour = 1, day = 14),
                keepFromMillis = EpgRetentionPolicy.keepFrom(now, kyiv),
                keepUntilMillis = EpgRetentionPolicy.keepUntil(now, kyiv),
            ),
        )
    }

    /**
     * The far end is calendar arithmetic for the same reason [DayScheduleBuilder]'s is: Europe/Kyiv
     * turns its clocks back on the last Sunday of October, making that day 25 hours long. Counting
     * `3 * 24h` from the 24th lands an hour early and drops the last hour of the 26th.
     */
    @Test
    fun theFarEndSurvivesTheAutumnClockChange() {
        val beforeTheChange = ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, kyiv).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 10, 27, 0, 0, 0, 0, kyiv).toInstant().toEpochMilli()

        val keepUntil = EpgRetentionPolicy.keepUntil(beforeTheChange, kyiv)

        assertEquals(expected, keepUntil)
        assertEquals(
            "the 25th is 25 hours long, so three days is 73 hours here",
            73L * 3_600_000L,
            keepUntil - EpgRetentionPolicy.keepFrom(beforeTheChange, kyiv),
        )
    }
}
