package com.uacastplayer.log

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What kept a 500-entry diagnostics buffer down to 20 usable entries: one line, written once a
 * second, saying the same thing every time.
 */
class RepeatedNoteFilterTest {

    @Test
    fun theFirstNoteIsAlwaysWorthLogging() {
        assertTrue(RepeatedNoteFilter().isWorthLogging("cast artwork: none, candidates=0"))
    }

    @Test
    fun theSameNoteAgainIsNot() {
        val filter = RepeatedNoteFilter()
        filter.isWorthLogging("cast artwork: none, candidates=0")
        assertFalse(filter.isWorthLogging("cast artwork: none, candidates=0"))
    }

    /** A changed verdict is news and must get through - this is a filter on repetition, not on the
     * subject. */
    @Test
    fun aChangedNoteGetsThrough() {
        val filter = RepeatedNoteFilter()
        filter.isWorthLogging("cast artwork: none, candidates=0")
        assertTrue(filter.isWorthLogging("cast artwork: none, candidates=2"))
    }

    /**
     * Only one note deep, deliberately. A channel that alternates between two verdicts is
     * describing something real each time; suppressing the second because it was seen before would
     * hide a flapping state, which is the opposite of the point.
     */
    @Test
    fun aVerdictThatAlternatesIsReportedEveryTime() {
        val filter = RepeatedNoteFilter()
        assertTrue(filter.isWorthLogging("a"))
        assertTrue(filter.isWorthLogging("b"))
        assertTrue(filter.isWorthLogging("a"))
        assertTrue(filter.isWorthLogging("b"))
    }

    /** Two filters are two independent subjects and must not share a memory. */
    @Test
    fun oneFiltersSilenceIsNotAnothers() {
        val first = RepeatedNoteFilter()
        val second = RepeatedNoteFilter()
        first.isWorthLogging("same")
        assertTrue(second.isWorthLogging("same"))
    }
}
