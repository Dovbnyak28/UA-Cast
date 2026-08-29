package com.uacastplayer.data.cast

import com.uacastplayer.core.cast.TsSourceKind
import org.junit.Assert.assertEquals
import org.junit.Test

private const val PACKET_SIZE = 188

private fun rawTsBytes(packetCount: Int = 2): ByteArray {
    val bytes = ByteArray(packetCount * PACKET_SIZE)
    for (packet in 0 until packetCount) bytes[packet * PACKET_SIZE] = 0x47
    return bytes
}

class TsSourceClassifierTest {

    @Test
    fun `playlist magic bytes classify as HLS regardless of content type`() {
        val prefix = "#EXTM3U\n#EXT-X-VERSION:3\nsegment0.ts\n".toByteArray()
        assertEquals(TsSourceKind.Hls, TsSourceClassifier.classify(contentType = null, prefixBytes = prefix))
    }

    @Test
    fun `an mpegurl content type classifies as HLS even without the magic bytes prefix`() {
        assertEquals(
            TsSourceKind.Hls,
            TsSourceClassifier.classify(contentType = "application/x-mpegURL", prefixBytes = ByteArray(0)),
        )
    }

    @Test
    fun `TS sync bytes one packet apart classify as raw TS`() {
        assertEquals(TsSourceKind.RawTs, TsSourceClassifier.classify(contentType = null, prefixBytes = rawTsBytes()))
    }

    @Test
    fun `a video content type does not override a raw TS byte pattern`() {
        assertEquals(
            TsSourceKind.RawTs,
            TsSourceClassifier.classify(contentType = "video/mp2t", prefixBytes = rawTsBytes()),
        )
    }

    @Test
    fun `neither playlist magic nor TS sync bytes is unknown`() {
        assertEquals(
            TsSourceKind.Unknown,
            TsSourceClassifier.classify(contentType = "text/html", prefixBytes = "<html></html>".toByteArray()),
        )
    }

    @Test
    fun `empty bytes with no content type is unknown`() {
        assertEquals(TsSourceKind.Unknown, TsSourceClassifier.classify(contentType = null, prefixBytes = ByteArray(0)))
    }
}
