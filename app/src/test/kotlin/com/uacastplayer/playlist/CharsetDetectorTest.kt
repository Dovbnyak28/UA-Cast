package com.uacastplayer.playlist

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val WINDOWS_1251 = Charset.forName("windows-1251")

class CharsetDetectorTest {

    @Test
    fun `plain ASCII M3U detects as UTF-8`() {
        val text = "#EXTM3U\n#EXTINF:-1,Channel One\nhttp://example.com/1"
        assertEquals(Charsets.UTF_8, CharsetDetector.detect(text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `UTF-8 with a Cyrillic channel name detects as UTF-8`() {
        val text = "#EXTM3U\n#EXTINF:-1,Перший канал\nhttp://example.com/1"
        assertEquals(Charsets.UTF_8, CharsetDetector.detect(text.toByteArray(Charsets.UTF_8)))
        assertEquals(text, CharsetDetector.decode(text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `a UTF-8 BOM is trusted outright`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + "#EXTM3U\n".toByteArray(Charsets.UTF_8)
        assertEquals(Charsets.UTF_8, CharsetDetector.detect(bytes))
    }

    @Test
    fun `a UTF-16LE BOM is trusted outright`() {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val bytes = bom + "#EXTM3U\n".toByteArray(Charsets.UTF_16LE)
        assertEquals(Charsets.UTF_16LE, CharsetDetector.detect(bytes))
    }

    @Test
    fun `a UTF-16BE BOM is trusted outright`() {
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val bytes = bom + "#EXTM3U\n".toByteArray(Charsets.UTF_16BE)
        assertEquals(Charsets.UTF_16BE, CharsetDetector.detect(bytes))
    }

    @Test
    fun `windows-1251 bytes with real Cyrillic text roundtrip correctly, not as UTF-8 mojibake`() {
        // Exactly the shape a real Ukrainian/Russian IPTV provider serves: a channel name that is
        // valid CP1251 but NOT valid UTF-8 (multi-byte UTF-8 has a strict self-checking structure
        // that arbitrary CP1251 Cyrillic bytes essentially never happen to satisfy).
        val original = "#EXTM3U\n#EXTINF:-1,Перший канал\nhttp://example.com/1"
        val cp1251Bytes = original.toByteArray(WINDOWS_1251)

        val detected = CharsetDetector.detect(cp1251Bytes)
        assertEquals(WINDOWS_1251, detected)
        assertEquals(original, CharsetDetector.decode(cp1251Bytes))
    }

    @Test
    fun `arbitrary non-UTF-8 bytes fall back to windows-1251 without throwing`() {
        val garbage = byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0x80.toByte(), 0xC0.toByte(), 0xFE.toByte())
        val decoded = CharsetDetector.decode(garbage)
        assertTrue(decoded.isNotEmpty())
    }

    @Test
    fun `an empty document is treated as valid UTF-8`() {
        assertEquals(Charsets.UTF_8, CharsetDetector.detect(ByteArray(0)))
    }
}
