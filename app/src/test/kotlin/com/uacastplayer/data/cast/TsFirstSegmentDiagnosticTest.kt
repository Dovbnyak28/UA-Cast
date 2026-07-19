package com.uacastplayer.data.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TsFirstSegmentDiagnosticTest {

    @Test
    fun `finds the first non-tag line in a media playlist`() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            segment0.ts
            #EXTINF:6.0,
            segment1.ts
        """.trimIndent()
        assertEquals("segment0.ts", TsFirstSegmentDiagnostic.firstMediaSegmentLine(playlist))
    }

    @Test
    fun `skips blank lines between tags`() {
        val playlist = "#EXTM3U\n\n#EXT-X-VERSION:3\n\nsegment0.ts\n"
        assertEquals("segment0.ts", TsFirstSegmentDiagnostic.firstMediaSegmentLine(playlist))
    }

    @Test
    fun `trims surrounding whitespace from the segment reference`() {
        val playlist = "#EXTM3U\n  segment0.ts  \n"
        assertEquals("segment0.ts", TsFirstSegmentDiagnostic.firstMediaSegmentLine(playlist))
    }

    @Test
    fun `a playlist with only tag lines and no references returns null`() {
        val playlist = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-ENDLIST\n"
        assertNull(TsFirstSegmentDiagnostic.firstMediaSegmentLine(playlist))
    }

    @Test
    fun `an empty playlist returns null`() {
        assertNull(TsFirstSegmentDiagnostic.firstMediaSegmentLine(""))
    }
}
