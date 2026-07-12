package com.uacastplayer.epg

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgSnapshotCodecTest {

    private fun roundTrip(snapshot: EpgSnapshot): EpgSnapshot? {
        val bytes = ByteArrayOutputStream().also { EpgSnapshotCodec.encode(snapshot, it) }.toByteArray()
        return EpgSnapshotCodec.decode(ByteArrayInputStream(bytes))
    }

    @Test
    fun `round-trips a snapshot with a document payload`() {
        val snapshot = EpgSnapshot(
            sourceFingerprint = "abc123",
            savedAtEpochMillis = 1_700_000_000_000L,
            documentBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
        )
        assertEquals(snapshot, roundTrip(snapshot))
    }

    @Test
    fun `round-trips an empty payload`() {
        val snapshot = EpgSnapshot("fp", 0L, ByteArray(0))
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
                flush()
            }
        }.toByteArray()
        assertNull(EpgSnapshotCodec.decode(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `decoding truncated data returns null instead of throwing`() {
        val bytes = ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).apply {
                writeInt(1)
                writeUTF("fp")
                flush()
            }
        }.toByteArray()
        assertNull(EpgSnapshotCodec.decode(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `decoding an empty stream returns null`() {
        assertNull(EpgSnapshotCodec.decode(ByteArrayInputStream(ByteArray(0))))
    }
}
