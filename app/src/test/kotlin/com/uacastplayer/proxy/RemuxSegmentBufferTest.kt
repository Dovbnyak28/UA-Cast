package com.uacastplayer.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun segment(sequence: Int, byteCount: Int): TsSegment =
    TsSegment(sequence, ByteArray(byteCount), durationMillis = 5_000)

class RemuxSegmentBufferTest {

    @Test
    fun `starts empty`() {
        val buffer = RemuxSegmentBuffer(maxBytes = 1_000)
        assertTrue(buffer.isEmpty)
        assertNull(buffer.firstSequence)
        assertEquals(emptyList<TsSegment>(), buffer.snapshot())
    }

    @Test
    fun `keeps segments under the byte cap without evicting`() {
        val buffer = RemuxSegmentBuffer(maxBytes = 1_000)
        buffer.add(segment(0, 300))
        buffer.add(segment(1, 300))
        assertEquals(listOf(0, 1), buffer.snapshot().map { it.sequence })
        assertEquals(0, buffer.firstSequence)
    }

    @Test
    fun `evicts the oldest segment first once the cap is exceeded`() {
        val buffer = RemuxSegmentBuffer(maxBytes = 500)
        // Seven segments, not two: eviction stops at the six-segment floor, so the oldest only goes
        // once a seventh arrives to replace it. See MIN_SEGMENTS for why the floor exists.
        repeat(7) { index -> buffer.add(segment(index, 300)) }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), buffer.snapshot().map { it.sequence })
        assertEquals(1, buffer.firstSequence)
    }

    @Test
    fun `evicts multiple old segments in one call if needed to get back under the cap`() {
        val buffer = RemuxSegmentBuffer(maxBytes = 500)
        repeat(7) { index -> buffer.add(segment(index, 50)) }
        // 350 so far, all held. This one pushes the total to 950 - segments 0..2 must all go in
        // this single add() to get back under 500, and eviction stops at the six-segment floor.
        buffer.add(segment(7, 600))
        assertEquals(listOf(2, 3, 4, 5, 6, 7), buffer.snapshot().map { it.sequence })
    }

    @Test
    fun `never evicts a segment that alone exceeds the cap`() {
        val buffer = RemuxSegmentBuffer(maxBytes = 100)
        buffer.add(segment(0, 500))
        assertEquals(listOf(0), buffer.snapshot().map { it.sequence })
    }

    @Test
    fun `segment looks up a still-buffered sequence`() {
        val buffer = RemuxSegmentBuffer(maxBytes = 1_000)
        buffer.add(segment(0, 100))
        buffer.add(segment(1, 100))
        assertEquals(1, buffer.segment(1)?.sequence)
    }

    @Test
    fun `segment returns null for an evicted sequence`() {
        val buffer = RemuxSegmentBuffer(maxBytes = 150)
        // Nothing is dropped until there is a seventh segment to drop the oldest for.
        repeat(7) { index -> buffer.add(segment(index, 100)) }
        assertNull(buffer.segment(0))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), buffer.snapshot().map { it.sequence })
    }

    /**
     * The byte ceiling is a memory guard, and on a high-bitrate channel it is reached with very few
     * segments held - it must not be allowed to squeeze the window down to something the receiver
     * cannot play. A client starts back from the live edge, so too short a window leaves it nothing
     * to start on and puts it right on the eviction edge.
     */
    @Test
    fun `never evicts below the segment floor even when far over the byte ceiling`() {
        val buffer = RemuxSegmentBuffer(maxBytes = 10)
        repeat(6) { index -> buffer.add(segment(index, 1_000)) }

        assertEquals(listOf(0, 1, 2, 3, 4, 5), buffer.snapshot().map { it.sequence })
        assertEquals(0, buffer.firstSequence)
    }

    @Test
    fun `holds exactly the segment floor once the ceiling is exceeded`() {
        val buffer = RemuxSegmentBuffer(maxBytes = 10)
        repeat(20) { index -> buffer.add(segment(index, 1_000)) }

        assertEquals(listOf(14, 15, 16, 17, 18, 19), buffer.snapshot().map { it.sequence })
        // MEDIA-SEQUENCE has to track the window sliding, not restart - see LiveHlsPlaylistBuilder.
        assertEquals(14, buffer.firstSequence)
    }
}
