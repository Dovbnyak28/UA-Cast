package com.uacastplayer.playlist

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /**
     * A file written by a newer build must be recognisable as such, not merely unreadable.
     *
     * The two look identical to [PlaylistSourceCodec.decode] - both come back empty - and the
     * handling has to be opposite: a corrupt file is worth nothing and overwriting it loses
     * nothing, while a newer one holds every playlist of someone who has just rolled a release
     * back. `PlaylistSourceStore.save` refuses to overwrite the second.
     */
    @Test
    fun `a file from a newer format is recognised rather than treated as corrupt`() {
        val fromTheFuture = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeInt(99)
                writeInt(0)
                flush()
            }
        }.toByteArray()

        assertTrue(PlaylistSourceCodec.isFromANewerFormat(ByteArrayInputStream(fromTheFuture)))
        assertEquals(emptyList<PlaylistSource>(), PlaylistSourceCodec.decode(ByteArrayInputStream(fromTheFuture)))
    }

    /** Today's own format is not "newer", or the app could never save anything again. */
    @Test
    fun `the current format is not mistaken for a newer one`() {
        val current = ByteArrayOutputStream().also { PlaylistSourceCodec.encode(emptyList(), it) }.toByteArray()

        assertFalse(PlaylistSourceCodec.isFromANewerFormat(ByteArrayInputStream(current)))
    }

    /** Neither is rubbish: a corrupt file has to stay overwritable. */
    @Test
    fun `a truncated or empty file is not mistaken for a newer one`() {
        assertFalse(PlaylistSourceCodec.isFromANewerFormat(ByteArrayInputStream(ByteArray(0))))
        assertFalse(PlaylistSourceCodec.isFromANewerFormat(ByteArrayInputStream(byteArrayOf(0, 1))))
    }
}
