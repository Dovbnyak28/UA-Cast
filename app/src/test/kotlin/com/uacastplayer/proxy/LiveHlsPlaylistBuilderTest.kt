package com.uacastplayer.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun segment(sequence: Int, durationMillis: Long, discontinuity: Boolean = false): TsSegment =
    TsSegment(sequence, bytes = ByteArray(0), durationMillis = durationMillis, discontinuity = discontinuity)

private fun url(sequence: Int) = "https://phone.local/hls/token/resource/seg$sequence.ts"

class LiveHlsPlaylistBuilderTest {

    @Test
    fun `builds a well-formed header with no ENDLIST`() {
        val playlist = LiveHlsPlaylistBuilder.build(emptyList(), ::url, configuredTargetDurationSeconds = 5)
        assertTrue(playlist.startsWith("#EXTM3U\n"))
        assertTrue(playlist.contains("#EXT-X-VERSION:3\n"))
        assertTrue(playlist.contains("#EXT-X-TARGETDURATION:5\n"))
        assertTrue(playlist.contains("#EXT-X-MEDIA-SEQUENCE:0\n"))
        assertTrue(!playlist.contains("#EXT-X-ENDLIST"))
    }

    @Test
    fun `media sequence is the first listed segment's own sequence number`() {
        val segments = listOf(segment(7, 5_000), segment(8, 5_000))
        val playlist = LiveHlsPlaylistBuilder.build(segments, ::url, configuredTargetDurationSeconds = 5)
        assertTrue(playlist.contains("#EXT-X-MEDIA-SEQUENCE:7\n"))
    }

    @Test
    fun `lists every segment with its EXTINF duration and URL in order`() {
        val segments = listOf(segment(0, 5_000), segment(1, 4_500))
        val playlist = LiveHlsPlaylistBuilder.build(segments, ::url, configuredTargetDurationSeconds = 5)
        val expectedOrder = listOf("#EXTINF:5.000,", url(0), "#EXTINF:4.500,", url(1))
        val lines = playlist.lines().filter { it.isNotBlank() }
        assertEquals(expectedOrder, lines.takeLast(expectedOrder.size))
    }

    @Test
    fun `target duration grows to cover a segment longer than the configured target`() {
        val segments = listOf(segment(0, 7_200))
        val playlist = LiveHlsPlaylistBuilder.build(segments, ::url, configuredTargetDurationSeconds = 5)
        assertTrue(playlist.contains("#EXT-X-TARGETDURATION:8\n"))
    }

    @Test
    fun `target duration never shrinks below the configured value`() {
        val segments = listOf(segment(0, 1_000))
        val playlist = LiveHlsPlaylistBuilder.build(segments, ::url, configuredTargetDurationSeconds = 5)
        assertTrue(playlist.contains("#EXT-X-TARGETDURATION:5\n"))
    }

    @Test
    fun `emits EXT-X-DISCONTINUITY immediately before a discontinuous segment only`() {
        val segments = listOf(segment(0, 5_000), segment(1, 5_000, discontinuity = true), segment(2, 5_000))
        val playlist = LiveHlsPlaylistBuilder.build(segments, ::url, configuredTargetDurationSeconds = 5)
        val lines = playlist.lines().filter { it.isNotBlank() }
        val expectedTail = listOf(
            "#EXTINF:5.000,", url(0),
            "#EXT-X-DISCONTINUITY", "#EXTINF:5.000,", url(1),
            "#EXTINF:5.000,", url(2),
        )
        assertEquals(expectedTail, lines.takeLast(expectedTail.size))
    }

    @Test
    fun `media sequence numbering is unaffected by a discontinuity`() {
        val segments = listOf(segment(5, 5_000), segment(6, 5_000, discontinuity = true))
        val playlist = LiveHlsPlaylistBuilder.build(segments, ::url, configuredTargetDurationSeconds = 5)
        assertTrue(playlist.contains("#EXT-X-MEDIA-SEQUENCE:5\n"))
    }

    /**
     * The receiver's own default is three segments back from the live edge, which is however many
     * seconds three segments happen to be - and all the slack it ever gets when upstream (a VPN,
     * typically) delivers slower than real time. Half the window scales with the window instead.
     */
    @Test
    fun `starts the receiver half a window back from the live edge`() {
        val segments = (0 until 6).map { segment(it, 5_000) }
        val playlist = LiveHlsPlaylistBuilder.build(segments, ::url, configuredTargetDurationSeconds = 5)
        // 6 x 5s = 30s of window, so 15s back - three segments' worth, and it grows with the window.
        assertTrue(playlist.contains("#EXT-X-START:TIME-OFFSET=-15.000,PRECISE=YES\n"))
    }

    @Test
    fun `the start offset tracks a shorter-segment window rather than a fixed number of seconds`() {
        // What a high-bitrate channel produces: TsSegmenter's byte cap forces cuts well short of
        // the 5s target, so a fixed offset would point outside the window.
        val segments = (0 until 8).map { segment(it, 2_000) }
        val playlist = LiveHlsPlaylistBuilder.build(segments, ::url, configuredTargetDurationSeconds = 5)
        assertTrue(playlist.contains("#EXT-X-START:TIME-OFFSET=-8.000,PRECISE=YES\n"))
    }

    @Test
    fun `omits the start offset while the window is still too short to place one`() {
        val playlist = LiveHlsPlaylistBuilder.build(
            (0 until 3).map { segment(it, 5_000) },
            ::url,
            configuredTargetDurationSeconds = 5,
        )
        assertTrue(!playlist.contains("#EXT-X-START"))
    }

    @Test
    fun `omits the start offset for an empty playlist`() {
        val playlist = LiveHlsPlaylistBuilder.build(emptyList(), ::url, configuredTargetDurationSeconds = 5)
        assertTrue(!playlist.contains("#EXT-X-START"))
    }

    /** The offset must sit inside the listed segments - a receiver told to start before the first
     * one has nowhere to begin, and on the eviction edge at that. */
    @Test
    fun `the start offset never exceeds the window it is placed in`() {
        for (count in 4..12) {
            val segments = (0 until count).map { segment(it, 5_000) }
            val playlist = LiveHlsPlaylistBuilder.build(segments, ::url, configuredTargetDurationSeconds = 5)
            val offset = playlist.substringAfter("#EXT-X-START:TIME-OFFSET=-").substringBefore(',').toDouble()
            assertTrue("offset $offset outside a ${count * 5}s window", offset < count * 5.0)
        }
    }
}
