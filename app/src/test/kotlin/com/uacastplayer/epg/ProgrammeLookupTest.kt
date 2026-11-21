package com.uacastplayer.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgrammeLookupTest {

    private fun programme(start: Long, stop: Long, title: String) =
        EpgProgramme(channelId = "ch1", startMillis = start, stopMillis = stop, title = title)

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

    /**
     * The mirror of the "before the first programme" case above, which was handled and this was
     * not. Past the end of a channel's listings the search still returns the last programme, so the
     * channel row showed a finished programme with a live dot and a full progress bar - and went on
     * showing it, since nothing after the last programme ever changes the answer. Feeds are ragged:
     * a channel whose listings end sooner than the rest hits this while the guide is otherwise fine.
     */
    @Test
    fun `past the end of the listings there is no current programme`() {
        val result = ProgrammeLookup.currentAndNext(programmes, nowMillis = 4500)
        assertNull("C ended at 4000", result.current)
        assertNull(result.next)
    }

    /** The boundary itself: a programme is over at its declared stop, not after it. */
    @Test
    fun `exactly at the last programme's declared stop it is over`() {
        assertNull(ProgrammeLookup.currentAndNext(programmes, nowMillis = 4000).current)
    }

    /**
     * A feed that gives no stop time leaves [EpgProgramme] with stop == start, which says nothing
     * about when the programme ends - so it is left alone rather than declared over the instant it
     * begins. Guessing "finished" here would blank the badge for every channel on such a feed.
     */
    @Test
    fun `a last programme with no declared duration is not treated as finished`() {
        val untimed = listOf(programme(1000, 1000, "Untimed"))
        val result = ProgrammeLookup.currentAndNext(untimed, nowMillis = 9999)
        assertEquals("Untimed", result.current?.title)
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
