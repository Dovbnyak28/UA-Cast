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
        // Four segments over the cap, not two: eviction stops at the three-segment floor a live
        // HLS playlist needs, so the oldest only goes once a fourth arrives to replace it.
        buffer.add(segment(0, 300))
        buffer.add(segment(1, 300))
        buffer.add(segment(2, 300))
        buffer.add(segment(3, 300))
        assertEquals(listOf(1, 2, 3), buffer.snapshot().map { it.sequence })
        assertEquals(1, buffer.firstSequence)
    }

    @Test
    fun `evicts multiple old segments in one call if needed to get back under the cap`() {
        val buffer = RemuxSegmentBuffer(maxBytes = 500)
        buffer.add(segment(0, 100))
        buffer.add(segment(1, 100))
        buffer.add(segment(2, 100))
        buffer.add(segment(3, 100))
        // Pushes the total to 700 - both segment 0 and segment 1 must go to get back under 500.
        buffer.add(segment(4, 300))
        assertEquals(listOf(2, 3, 4), buffer.snapshot().map { it.sequence })
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
        // Four, not two: eviction never takes the window below the three segments a live HLS
        // playlist needs, so nothing is dropped until there is a fourth to drop it for.
        buffer.add(segment(0, 100))
        buffer.add(segment(1, 100))
        buffer.add(segment(2, 100))
        buffer.add(segment(3, 100))
        assertNull(buffer.segment(0))
        assertEquals(listOf(1, 2, 3), buffer.snapshot().map { it.sequence })
    }

    /**
     * An HLS client starts three segments back from the live edge, so a window shorter than that
     * gives it nothing to start on. The byte ceiling is a memory guard and can be hit with very few
     * segments on a high-bitrate channel - it must not be allowed to starve the playlist.
     */
    @Test
    fun `never evicts below three segments even when far over the byte ceiling`() {
        val buffer = RemuxSegmentBuffer(maxBytes = 10)
        buffer.add(segment(0, 1_000))
        buffer.add(segment(1, 1_000))
        buffer.add(segment(2, 1_000))

        assertEquals(listOf(0, 1, 2), buffer.snapshot().map { it.sequence })
        assertEquals(0, buffer.firstSequence)
    }

    @Test
    fun `keeps exactly three segments once the ceiling is exceeded`() {
        val buffer = RemuxSegmentBuffer(maxBytes = 10)
        repeat(20) { index -> buffer.add(segment(index, 1_000)) }

        assertEquals(listOf(17, 18, 19), buffer.snapshot().map { it.sequence })
        // MEDIA-SEQUENCE has to track the window sliding, not restart - see LiveHlsPlaylistBuilder.
        assertEquals(17, buffer.firstSequence)
    }
}
