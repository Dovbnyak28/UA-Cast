package com.uacastplayer.epg

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgSnapshotCodecTest {

    private fun encode(header: EpgSnapshotHeader, document: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out ->
            EpgSnapshotCodec.encode(header, ByteArrayInputStream(document), document.size.toLong(), out)
        }.toByteArray()

    @Test
    fun `round-trips a header and document payload`() {
        val header = EpgSnapshotHeader(sourceFingerprint = "abc123", savedAtEpochMillis = 1_700_000_000_000L)
        val document = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val encoded = encode(header, document)

        val decoded = EpgSnapshotCodec.decodeHeader(ByteArrayInputStream(encoded))

        assertEquals(header, decoded?.header)
        assertArrayEquals(document, decoded?.documentStream?.readBytes())
    }

    @Test
    fun `round-trips an empty payload`() {
        val header = EpgSnapshotHeader("fp", 0L)
        val decoded = EpgSnapshotCodec.decodeHeader(ByteArrayInputStream(encode(header, ByteArray(0))))

        assertEquals(header, decoded?.header)
        assertArrayEquals(ByteArray(0), decoded?.documentStream?.readBytes())
    }

    @Test
    fun `decoding an unknown format version returns null`() {
        val bytes = ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).apply {
                writeInt(999)
                writeUTF("fp")
                writeLong(0L)
                writeLong(0L)
                flush()
            }
        }.toByteArray()
        assertNull(EpgSnapshotCodec.decodeHeader(ByteArrayInputStream(bytes)))
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
        assertNull(EpgSnapshotCodec.decodeHeader(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `decoding an empty stream returns null`() {
        assertNull(EpgSnapshotCodec.decodeHeader(ByteArrayInputStream(ByteArray(0))))
    }
}
