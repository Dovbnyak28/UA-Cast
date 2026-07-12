package com.uacastplayer.playlist

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistSnapshotCodecTest {

    private fun roundTrip(snapshot: PlaylistSnapshot): PlaylistSnapshot? {
        val bytes = ByteArrayOutputStream().also { PlaylistSnapshotCodec.encode(snapshot, it) }.toByteArray()
        return PlaylistSnapshotCodec.decode(ByteArrayInputStream(bytes))
    }

    @Test
    fun `round-trips a snapshot with fully populated channels`() {
        val snapshot = PlaylistSnapshot(
            sourceFingerprint = "abc123",
            savedAtEpochMillis = 1_700_000_000_000L,
            channels = listOf(
                M3uChannel(
                    displayName = "Channel One",
                    streamUrl = "http://example.com/1.m3u8",
                    tvgId = "ch1",
                    tvgName = "Channel One",
                    tvgLogo = "http://example.com/1.png",
                    groupTitle = "News",
                )
            ),
            skippedLineCount = 2,
        )
        assertEquals(snapshot, roundTrip(snapshot))
    }

    @Test
    fun `round-trips a snapshot with null optional channel fields`() {
        val snapshot = PlaylistSnapshot(
            sourceFingerprint = "def456",
            savedAtEpochMillis = 0L,
            channels = listOf(M3uChannel(displayName = "Channel", streamUrl = "http://example.com/1.m3u8")),
            skippedLineCount = 0,
        )
        assertEquals(snapshot, roundTrip(snapshot))
    }

    @Test
    fun `round-trips a snapshot with no channels`() {
        val snapshot = PlaylistSnapshot("fp", 123L, emptyList(), 0)
        assertEquals(snapshot, roundTrip(snapshot))
    }

    @Test
    fun `round-trips many channels preserving order`() {
        val channels = (1..50).map { M3uChannel(displayName = "Ch $it", streamUrl = "http://example.com/$it.m3u8") }
        val snapshot = PlaylistSnapshot("fp", 1L, channels, 0)
        assertEquals(snapshot, roundTrip(snapshot))
    }

    @Test
    fun `decoding an unknown format version returns null`() {
        val bytes = ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).apply {
                writeInt(999)
                writeUTF("fp")
                writeLong(0L)
                writeInt(0)
                writeInt(0)
                flush()
            }
        }.toByteArray()
        assertNull(PlaylistSnapshotCodec.decode(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `decoding truncated data returns null instead of throwing`() {
        val bytes = ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).apply {
                writeInt(1)
                writeUTF("fp")
                // Truncated: missing savedAtEpochMillis, skippedLineCount, channel count, etc.
                flush()
            }
        }.toByteArray()
        assertNull(PlaylistSnapshotCodec.decode(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `decoding an empty stream returns null instead of throwing`() {
        assertNull(PlaylistSnapshotCodec.decode(ByteArrayInputStream(ByteArray(0))))
    }
}
