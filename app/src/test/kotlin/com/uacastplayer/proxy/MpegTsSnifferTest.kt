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
    fun `a body too short to hold both sync bytes never looks like TS`() {
        // Even with a valid sync byte first - a real live TS stream is never this short, while a
        // short unrelated body starting with 0x47 absolutely happens (see the 'G' test below).
        val bytes = ByteArray(PACKET_SIZE) { 0 }
        bytes[0] = 0x47
        assertFalse(MpegTsSniffer.looksLikeMpegTs(bytes))
    }

    @Test
    fun `a short plain-text error starting with the letter G does not look like TS`() {
        // 0x47 is ASCII 'G' - an upstream serving "Gateway Time-out" with a 200 status used to
        // classify as MPEG-TS and spin up a remux session on garbage.
        assertFalse(MpegTsSniffer.looksLikeMpegTs("Gateway Time-out".toByteArray()))
    }

    @Test
    fun `exactly one full packet plus one sync byte is the minimum that looks like TS`() {
        val bytes = ByteArray(PACKET_SIZE + 1)
        bytes[0] = 0x47
        bytes[PACKET_SIZE] = 0x47
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
