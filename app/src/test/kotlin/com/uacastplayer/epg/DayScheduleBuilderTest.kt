package com.uacastplayer.epg

import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayScheduleBuilderTest {

    private val zone: ZoneId = ZoneOffset.UTC

    private fun programme(id: String, startMillis: Long, stopMillis: Long) =
        EpgProgramme(channelId = "ch", startMillis = startMillis, stopMillis = stopMillis, title = id)

    // 2026-01-01T00:00:00Z
    private val dayStart = 1_767_225_600_000L
    private val hour = 60 * 60 * 1000L

    @Test
    fun `splits programmes into past current and upcoming`() {
        val past = programme("past", dayStart, dayStart + hour)
        val current = programme("current", dayStart + hour, dayStart + 2 * hour)
        val upcoming = programme("upcoming", dayStart + 2 * hour, dayStart + 3 * hour)
        val nowMillis = dayStart + hour + hour / 2

        val schedule = DayScheduleBuilder.build(listOf(upcoming, past, current), nowMillis, zone)

        assertEquals(listOf(past), schedule.past)
        assertEquals(current, schedule.current)
        assertEquals(listOf(upcoming), schedule.upcoming)
    }

    @Test
    fun `programme spanning midnight from yesterday counts as todays current`() {
        val spansMidnight = programme("late-night", dayStart - hour, dayStart + hour)
        val nowMillis = dayStart + hour / 2

        val schedule = DayScheduleBuilder.build(listOf(spansMidnight), nowMillis, zone)

        assertEquals(spansMidnight, schedule.current)
        assertEquals(emptyList<EpgProgramme>(), schedule.past)
        assertEquals(emptyList<EpgProgramme>(), schedule.upcoming)
    }

    @Test
    fun `programme starting just before midnight tonight is included as upcoming`() {
        val lateProgramme = programme("late", dayStart + 23 * hour, dayStart + 25 * hour)
        val nowMillis = dayStart

        val schedule = DayScheduleBuilder.build(listOf(lateProgramme), nowMillis, zone)

        assertEquals(listOf(lateProgramme), schedule.upcoming)
    }

    @Test
    fun `programme entirely on the previous day is excluded`() {
        val yesterday = programme("yesterday", dayStart - 3 * hour, dayStart - hour)
        val nowMillis = dayStart + hour

        val schedule = DayScheduleBuilder.build(listOf(yesterday), nowMillis, zone)

        assertEquals(emptyList<EpgProgramme>(), schedule.past)
        assertNull(schedule.current)
        assertEquals(emptyList<EpgProgramme>(), schedule.upcoming)
    }

    @Test
    fun `programme entirely on the next day is excluded`() {
        val tomorrow = programme("tomorrow", dayStart + 25 * hour, dayStart + 27 * hour)
        val nowMillis = dayStart + hour

        val schedule = DayScheduleBuilder.build(listOf(tomorrow), nowMillis, zone)

        assertEquals(emptyList<EpgProgramme>(), schedule.upcoming)
    }

    @Test
    fun `empty input yields empty schedule`() {
        val schedule = DayScheduleBuilder.build(emptyList(), dayStart, zone)

        assertEquals(emptyList<EpgProgramme>(), schedule.past)
        assertNull(schedule.current)
        assertEquals(emptyList<EpgProgramme>(), schedule.upcoming)
    }

    /**
     * The clocks go back on the last Sunday of October, making that local day 25 hours long. A day
     * end computed as "start + 24h" lands at 23:00 and takes the evening's last hour of listings
     * with it - in Ukraine, prime time.
     */
    @Test
    fun `on the 25-hour day the last hour of the evening is still today`() {
        val kyiv = ZoneId.of("Europe/Kyiv")
        val lateShow = programme(
            "23:30 on the day the clocks go back",
            startMillis = ZonedDateTime.of(2026, 10, 25, 23, 30, 0, 0, kyiv).toInstant().toEpochMilli(),
            stopMillis = ZonedDateTime.of(2026, 10, 26, 0, 30, 0, 0, kyiv).toInstant().toEpochMilli(),
        )
        val nowMillis = ZonedDateTime.of(2026, 10, 25, 20, 0, 0, 0, kyiv).toInstant().toEpochMilli()

        val schedule = DayScheduleBuilder.build(listOf(lateShow), nowMillis, kyiv)

        assertEquals(listOf(lateShow), schedule.upcoming)
    }

    /**
     * And forward on the last Sunday of March, making that day 23 hours long - where the same
     * arithmetic overshoots instead, pulling the small hours of the *next* day into today's guide.
     */
    @Test
    fun `on the 23-hour day tomorrow morning does not leak into today`() {
        val kyiv = ZoneId.of("Europe/Kyiv")
        val tomorrow = programme(
            "00:30 the morning after the clocks go forward",
            startMillis = ZonedDateTime.of(2026, 3, 30, 0, 30, 0, 0, kyiv).toInstant().toEpochMilli(),
            stopMillis = ZonedDateTime.of(2026, 3, 30, 1, 30, 0, 0, kyiv).toInstant().toEpochMilli(),
        )
        val nowMillis = ZonedDateTime.of(2026, 3, 29, 20, 0, 0, 0, kyiv).toInstant().toEpochMilli()

        val schedule = DayScheduleBuilder.build(listOf(tomorrow), nowMillis, kyiv)

        assertEquals(emptyList<EpgProgramme>(), schedule.upcoming)
    }

    @Test
    fun `unsorted input is sorted before bucketing`() {
        val first = programme("first", dayStart, dayStart + hour)
        val second = programme("second", dayStart + hour, dayStart + 2 * hour)
        val third = programme("third", dayStart + 2 * hour, dayStart + 3 * hour)
        val nowMillis = dayStart + 3 * hour + hour / 2

        val schedule = DayScheduleBuilder.build(listOf(third, first, second), nowMillis, zone)

        assertEquals(listOf(first, second, third), schedule.past)
    }
}
