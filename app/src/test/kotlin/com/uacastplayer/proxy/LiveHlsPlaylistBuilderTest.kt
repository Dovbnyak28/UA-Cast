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
}
