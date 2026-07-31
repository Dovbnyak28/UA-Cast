package com.uacastplayer.playlist

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSourceCodecTest {

    private fun roundTrip(sources: List<PlaylistSource>): List<PlaylistSource> {
        val output = ByteArrayOutputStream()
        PlaylistSourceCodec.encode(sources, output)
        return PlaylistSourceCodec.decode(ByteArrayInputStream(output.toByteArray()))
    }

    @Test
    fun `round-trips an empty list`() {
        assertEquals(emptyList<PlaylistSource>(), roundTrip(emptyList()))
    }

    @Test
    fun `round-trips multiple sources of every type`() {
        val sources = listOf(
            PlaylistSource("id1", PlaylistSourceType.URL, "http://a.com/p.m3u", "A", 100L),
            PlaylistSource("id2", PlaylistSourceType.FILE, "content://x/y", null, 200L),
            PlaylistSource("id3", PlaylistSourceType.XTREAM, "http://b.com/get.php", "B", 300L),
        )
        assertEquals(sources, roundTrip(sources))
    }

    @Test
    fun `decoding garbage bytes yields an empty list rather than throwing`() {
        val garbage = byteArrayOf(1, 2, 3)
        assertEquals(emptyList<PlaylistSource>(), PlaylistSourceCodec.decode(ByteArrayInputStream(garbage)))
    }

    @Test
    fun `decoding an unknown format version yields an empty list`() {
        val output = ByteArrayOutputStream()
        java.io.DataOutputStream(output).apply {
            writeInt(999)
            flush()
        }
        val decoded = PlaylistSourceCodec.decode(ByteArrayInputStream(output.toByteArray()))
        assertEquals(emptyList<PlaylistSource>(), decoded)
    }

    @Test
    fun `null display name round-trips as null`() {
        val sources = listOf(PlaylistSource("id1", PlaylistSourceType.FILE, "content://x", null, 1L))
        val decoded = roundTrip(sources)
        assertTrue(decoded[0].displayName == null)
    }
}
