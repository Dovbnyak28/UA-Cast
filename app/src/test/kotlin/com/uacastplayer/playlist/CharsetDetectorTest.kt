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

    /**
     * The reported bug, reproduced end to end. A provider (Hetzner-hosted, in the report) serves a
     * genuinely UTF-8 M3U while its Content-Type says `charset=windows-1251`. Honouring that
     * declaration rendered each Cyrillic letter's two UTF-8 bytes as two separate Windows-1251
     * characters, so the channel list read "Liberty РЎРїРѕСЂС‚" instead of "Liberty Спорт".
     */
    @Test
    fun `a UTF-8 body wrongly declared as windows-1251 is decoded as UTF-8`() {
        val original = "#EXTM3U\n#EXTINF:-1,Liberty Спорт\nhttp://example.com/1"
        val utf8Bytes = original.toByteArray(Charsets.UTF_8)

        assertEquals(Charsets.UTF_8, CharsetDetector.detect(utf8Bytes, declared = WINDOWS_1251))
        assertEquals(original, String(utf8Bytes, CharsetDetector.detect(utf8Bytes, WINDOWS_1251)))
    }

    /** Guards the exact mis-decode the bug produced, rather than only the corrected output: had the
     * declaration been honoured, this is the string the user would have seen. */
    @Test
    fun `honouring the wrong declaration is what produced the mojibake`() {
        val utf8Bytes = "Liberty Спорт".toByteArray(Charsets.UTF_8)
        assertEquals("Liberty РЎРїРѕСЂС‚", String(utf8Bytes, WINDOWS_1251))
    }

    @Test
    fun `a genuinely windows-1251 body declared as such still uses the declaration`() {
        val original = "#EXTM3U\n#EXTINF:-1,Перший канал\nhttp://example.com/1"
        val cp1251Bytes = original.toByteArray(WINDOWS_1251)

        assertEquals(WINDOWS_1251, CharsetDetector.detect(cp1251Bytes, declared = WINDOWS_1251))
        assertEquals(original, String(cp1251Bytes, CharsetDetector.detect(cp1251Bytes, WINDOWS_1251)))
    }

    /** A declaration that names something other than this object's Windows-1251 guess is believed
     * once the bytes have ruled UTF-8 out - the server knows its own legacy encoding. */
    @Test
    fun `a non-UTF-8 body declared as KOI8-R uses KOI8-R, not the windows-1251 fallback`() {
        val koi8 = Charset.forName("KOI8-R")
        val bytes = "Перший канал".toByteArray(koi8)

        assertEquals(koi8, CharsetDetector.detect(bytes, declared = koi8))
    }

    /** The symmetric case: a server claiming UTF-8 over bytes that are not valid UTF-8 is
     * disbelieved too, so the text falls back instead of filling up with U+FFFD. */
    @Test
    fun `a windows-1251 body wrongly declared as UTF-8 falls back instead of yielding replacement chars`() {
        val original = "#EXTM3U\n#EXTINF:-1,Перший канал\nhttp://example.com/1"
        val cp1251Bytes = original.toByteArray(WINDOWS_1251)

        val detected = CharsetDetector.detect(cp1251Bytes, declared = Charsets.UTF_8)

        assertEquals(WINDOWS_1251, detected)
        assertEquals(original, String(cp1251Bytes, detected))
        assertTrue("decoded text must not contain U+FFFD", '�' !in String(cp1251Bytes, detected))
    }

    /** A BOM is written by whoever produced the bytes; the Content-Type is added by whatever is
     * serving them. The BOM wins even against a contradicting declaration. */
    @Test
    fun `a BOM beats a contradicting declared charset`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + "#EXTM3U\n".toByteArray(Charsets.UTF_8)

        assertEquals(Charsets.UTF_8, CharsetDetector.detect(bytes, declared = WINDOWS_1251))
    }

    @Test
    fun `with no declaration the two-argument form matches the one-argument form`() {
        val cp1251 = "Перший канал".toByteArray(WINDOWS_1251)
        val utf8 = "Перший канал".toByteArray(Charsets.UTF_8)

        assertEquals(CharsetDetector.detect(cp1251), CharsetDetector.detect(cp1251, declared = null))
        assertEquals(CharsetDetector.detect(utf8), CharsetDetector.detect(utf8, declared = null))
    }
}
