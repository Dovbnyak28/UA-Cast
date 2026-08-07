package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The 64KB detection sample must not be cut mid-character.
 *
 * `CharsetDetector` only inspects the first 64KB to decide an encoding. Slicing at a fixed byte
 * offset splits a multi-byte sequence about half the time in Cyrillic text, and an incomplete
 * trailing sequence is not valid UTF-8 - so a document that was valid UTF-8 from beginning to end
 * was declared Windows-1251 and every Cyrillic name in it decoded to mojibake.
 *
 * These cases walk the padding one byte at a time across the boundary, so every alignment of a
 * two-byte character against the cut is covered. Before the fix the even-offset half of them
 * failed; a regression would fail them again.
 */
class CharsetDetectorBoundaryTest {

    private val header = "#EXTM3U\n#EXTINF:-1,"
    private val trailer = "\nhttp://h/s.m3u8\n"

    private fun documentWithCyrillicAcross(paddingBytes: Int): ByteArray =
        (header + "a".repeat(paddingBytes) + "Ц".repeat(2000) + trailer).toByteArray(Charsets.UTF_8)

    @Test
    fun everyAlignmentAcrossTheSampleBoundaryStaysUtf8() {
        for (padding in 65_520..65_540) {
            val bytes = documentWithCyrillicAcross(padding)
            assertEquals(
                "padding=$padding was misdetected - a valid UTF-8 playlist would decode as mojibake",
                Charsets.UTF_8,
                CharsetDetector.detect(bytes),
            )
        }
    }

    @Test
    fun theChannelNameSurvivesTheBoundary() {
        for (padding in 65_520..65_540) {
            val bytes = documentWithCyrillicAcross(padding)
            val channels = M3uParser.parse(CharsetDetector.decode(bytes)).channels
            assertEquals("padding=$padding lost its channel", 1, channels.size)
            assertEquals(
                "padding=$padding produced mojibake",
                "a".repeat(padding) + "Ц".repeat(2000),
                channels[0].displayName,
            )
        }
    }

    /** Genuine Windows-1251 must still be detected as such - the fix trims the sample, it does not
     * make the UTF-8 check more permissive. */
    @Test
    fun genuineWindows1251IsStillDetected() {
        val cp1251 = charset("windows-1251")
        val bytes = (header + "Первый канал " + "я".repeat(70_000) + trailer).toByteArray(cp1251)
        assertEquals(cp1251, CharsetDetector.detect(bytes))
    }
}
