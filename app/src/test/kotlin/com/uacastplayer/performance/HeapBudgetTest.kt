package com.uacastplayer.performance

import com.uacastplayer.core.settings.BufferSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The budget that decides how much this app may hold.
 *
 * It exists because of a field crash the tier classifier could not have prevented: a moto e15 scores
 * well on RAM, cores and API level, and is given a **128MB** heap. It died of an OutOfMemoryError
 * inside the video decoder's input buffer, having been caught at 111MB used of 128MB in a
 * diagnostics report taken minutes earlier.
 */
class HeapBudgetTest {

    private fun mb(count: Long) = count * 1024 * 1024

    /** The device the budget was written from. Its guide has to come down, and by a lot. */
    @Test
    fun `the heap that crashed gets a guide it can actually hold`() {
        val allowed = HeapBudget.maxProgrammes(mb(128))

        assertTrue("expected well under the old fixed cap, got $allowed", allowed < 150_000)
        assertTrue("but still a usable guide, got $allowed", allowed >= HeapBudget.MIN_PROGRAMMES)
    }

    /**
     * The control, and the reason this is a budget rather than a smaller constant: a phone with
     * room must keep exactly what it had. 400,000 is the ceiling
     * [com.uacastplayer.epg.XmlTvParser] has always used.
     */
    @Test
    fun `a roomy heap keeps the full guide it always had`() {
        assertEquals(HeapBudget.MAX_PROGRAMMES, HeapBudget.maxProgrammes(mb(512)))
        assertEquals(HeapBudget.MAX_PROGRAMMES, HeapBudget.maxProgrammes(mb(1024)))
    }

    /** Never below the floor, however small the heap - an empty guide is worse than a thin one. */
    @Test
    fun `an absurdly small heap still gets the floor rather than nothing`() {
        assertEquals(HeapBudget.MIN_PROGRAMMES, HeapBudget.maxProgrammes(mb(32)))
        assertEquals(HeapBudget.MIN_PROGRAMMES, HeapBudget.maxProgrammes(0))
        assertEquals(HeapBudget.MIN_PROGRAMMES, HeapBudget.maxProgrammes(-1))
    }

    /** Monotonic: more heap must never buy a smaller guide. */
    @Test
    fun `more heap never means fewer programmes`() {
        var previous = 0
        for (heapMb in listOf(64L, 96L, 128L, 192L, 256L, 384L, 512L, 768L)) {
            val allowed = HeapBudget.maxProgrammes(mb(heapMb))
            assertTrue("${heapMb}MB gave $allowed, less than the step below ($previous)", allowed >= previous)
            previous = allowed
        }
    }

    /** The arithmetic must not overflow into a negative or a wrap on a very large heap. */
    @Test
    fun `a huge heap is capped rather than overflowing`() {
        assertEquals(HeapBudget.MAX_PROGRAMMES, HeapBudget.maxProgrammes(Long.MAX_VALUE))
    }

    /**
     * The media buffer, which is the other half of the fix: the crash happened inside the decoder's
     * own input buffer, and this is 16MB of held media coming down to 8MB.
     */
    @Test
    fun `a tight heap defaults to the smallest media buffer`() {
        assertEquals(BufferSize.SMALL, HeapBudget.defaultBufferSize(mb(128)))
        assertEquals(BufferSize.SMALL, HeapBudget.defaultBufferSize(mb(256)))
    }

    @Test
    fun `a roomy heap keeps the buffer default it always had`() {
        assertEquals(BufferSize.MEDIUM, HeapBudget.defaultBufferSize(mb(512)))
        assertEquals(BufferSize.MEDIUM, HeapBudget.defaultBufferSize(mb(320)))
    }

    /**
     * LARGE is for an unstable connection - a fact about the network, not the device - so no heap
     * may hand it out by itself. It stays something the user opts into.
     */
    @Test
    fun `no heap ever defaults to the largest buffer`() {
        for (heapMb in listOf(64L, 128L, 256L, 512L, 2048L, 8192L)) {
            assertTrue(HeapBudget.defaultBufferSize(mb(heapMb)) != BufferSize.LARGE)
        }
    }

    @Test
    fun `the tight-heap flag matches the device it was written from`() {
        assertTrue(HeapBudget.isTight(mb(128)))
        assertTrue(HeapBudget.isTight(mb(96)))
        assertFalse(HeapBudget.isTight(mb(192)))
    }
}
