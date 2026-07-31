package com.uacastplayer.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun sampleData() = BackupData(
        sources = listOf(
            BackupPlaylistSource("id1", "URL", "http://a.com/p.m3u", "A", 100L),
            BackupPlaylistSource("id2", "FILE", "content://x/y", null, 200L),
        ),
        favorites = listOf(
            BackupFavorite("k1", "Channel One", "http://a.com/1.m3u8", "tvg1", "News", 10L),
            BackupFavorite("k2", "Channel Two", "http://a.com/2.m3u8", null, null, 20L),
        ),
        settings = BackupSettings(
            iconDisplayMode = "CACHE",
            listDensity = "FULL",
            bufferSize = "MEDIUM",
            epgSourceId = "epg_it999_rect_transparent",
            epgCustomUrl = null,
        ),
    )

    @Test
    fun `round-trips a full backup`() {
        val data = sampleData()
        val decoded = BackupCodec.decode(BackupCodec.encode(data))
        assertEquals(data, decoded)
    }

    @Test
    fun `decoding a blank string yields null`() {
        assertNull(BackupCodec.decode(""))
        assertNull(BackupCodec.decode("   "))
    }

    @Test
    fun `decoding malformed JSON yields null rather than throwing`() {
        assertNull(BackupCodec.decode("{not valid json"))
    }

    @Test
    fun `decoding an unknown version yields null`() {
        val json = """{"version": 999, "sources": [], "favorites": [], "settings": {}}"""
        assertNull(BackupCodec.decode(json))
    }

    @Test
    fun `decoding with missing sources or favorites arrays yields empty lists`() {
        val json = """{"version": 1, "settings": {}}"""
        val decoded = BackupCodec.decode(json)
        assertEquals(emptyList<BackupPlaylistSource>(), decoded?.sources)
        assertEquals(emptyList<BackupFavorite>(), decoded?.favorites)
    }

    @Test
    fun `a source entry missing a required field is skipped rather than failing the whole import`() {
        val json = """
            {"version": 1, "sources": [{"type": "URL", "location": "http://a.com"}], "favorites": [], "settings": {}}
        """.trimIndent()
        val decoded = BackupCodec.decode(json)
        assertTrue(decoded?.sources.isNullOrEmpty())
    }

    @Test
    fun `null display name round-trips as null`() {
        val data = sampleData()
        val decoded = BackupCodec.decode(BackupCodec.encode(data))
        assertNull(decoded?.sources?.get(1)?.displayName)
    }
}
