package com.uacastplayer.proxy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PACKET_SIZE = 188

class MpegTsSnifferTest {

    private fun tsBytes(packetCount: Int, corruptSecondSync: Boolean = false): ByteArray {
        val bytes = ByteArray(packetCount * PACKET_SIZE)
        for (packet in 0 until packetCount) {
            val syncOffset = packet * PACKET_SIZE
            bytes[syncOffset] = if (corruptSecondSync && packet == 1) 0x00 else 0x47
        }
        return bytes
    }

    @Test
    fun `two sync bytes one packet apart looks like TS`() {
        assertTrue(MpegTsSniffer.looksLikeMpegTs(tsBytes(2)))
    }

    @Test
    fun `a mismatched second sync byte does not look like TS`() {
        assertFalse(MpegTsSniffer.looksLikeMpegTs(tsBytes(2, corruptSecondSync = true)))
    }

    @Test
    fun `fewer than two packets falls back to checking only the first byte`() {
        val bytes = ByteArray(PACKET_SIZE) { 0 }
        bytes[0] = 0x47
        assertTrue(MpegTsSniffer.looksLikeMpegTs(bytes))
    }

    @Test
    fun `empty bytes do not look like TS`() {
        assertFalse(MpegTsSniffer.looksLikeMpegTs(ByteArray(0)))
    }

    @Test
    fun `an HLS playlist prefix does not look like TS`() {
        assertFalse(MpegTsSniffer.looksLikeMpegTs("#EXTM3U\n#EXT-X-VERSION:3\n".toByteArray()))
    }
}
