package com.uacastplayer.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgrammeLookupTest {

    private fun programme(start: Long, stop: Long, title: String) =
        EpgProgramme(channelId = "ch1", startMillis = start, stopMillis = stop, title = title, description = null)

    private val programmes = listOf(
        programme(1000, 2000, "A"),
        programme(2000, 3000, "B"),
        programme(3000, 4000, "C"),
    )

    @Test
    fun `finds the current and next programme mid-slot`() {
        val result = ProgrammeLookup.currentAndNext(programmes, nowMillis = 2500)
        assertEquals("B", result.current?.title)
        assertEquals("C", result.next?.title)
        assertEquals(3000L, result.effectiveStopMillis)
    }

    @Test
    fun `effective stop uses the next programme's start, not the current one's own stop`() {
        val gappy = listOf(
            programme(1000, 1900, "A"), // declared stop 1900, but next starts at 2000
            programme(2000, 3000, "B"),
        )
        val result = ProgrammeLookup.currentAndNext(gappy, nowMillis = 1950)
        assertEquals("A", result.current?.title)
        assertEquals(2000L, result.effectiveStopMillis)
    }

    @Test
    fun `last programme falls back to its own declared stop when there is no next`() {
        val result = ProgrammeLookup.currentAndNext(programmes, nowMillis = 3500)
        assertEquals("C", result.current?.title)
        assertNull(result.next)
        assertEquals(4000L, result.effectiveStopMillis)
    }

    @Test
    fun `before the first programme returns no current but the first as next`() {
        val result = ProgrammeLookup.currentAndNext(programmes, nowMillis = 500)
        assertNull(result.current)
        assertEquals("A", result.next?.title)
        assertEquals(1000L, result.effectiveStopMillis)
    }

    @Test
    fun `exactly at a programme's start counts as current`() {
        val result = ProgrammeLookup.currentAndNext(programmes, nowMillis = 2000)
        assertEquals("B", result.current?.title)
    }

    @Test
    fun `empty list yields nothing`() {
        val result = ProgrammeLookup.currentAndNext(emptyList(), nowMillis = 1000)
        assertNull(result.current)
        assertNull(result.next)
        assertNull(result.effectiveStopMillis)
    }
}
