package com.uacastplayer.core.io

import org.junit.Assert.assertEquals
import org.junit.Test

class GzipSnifferTest {

    @Test
    fun `recognizes the gzip magic bytes`() {
        val bytes = byteArrayOf(0x1f, 0x8b.toByte(), 0x08, 0x00)
        assertEquals(true, GzipSniffer.isGzip(bytes))
    }

    @Test
    fun `rejects plain xml bytes`() {
        val bytes = "<?xml version=\"1.0\"?><tv></tv>".toByteArray()
        assertEquals(false, GzipSniffer.isGzip(bytes))
    }

    @Test
    fun `rejects empty and single byte input`() {
        assertEquals(false, GzipSniffer.isGzip(ByteArray(0)))
        assertEquals(false, GzipSniffer.isGzip(byteArrayOf(0x1f)))
    }
}
