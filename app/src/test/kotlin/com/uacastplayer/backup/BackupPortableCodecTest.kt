package com.uacastplayer.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupPortableCodecTest {
    @Test fun `version one document-only local source remains readable`() {
        val json = """{"version":1,"sources":[{"id":"old","type":"FILE","location":"content://old/file"}]}"""
        val decoded = BackupCodec.decode(json)!!
        assertEquals("content://old/file", decoded.sources.single().location)
        assertNull(decoded.sources.single().playlistBase64)
    }

    @Test fun `version two requires complete containers and valid rows`() {
        val bad = listOf(
            """{"version":2}""",
            """{"version":2,"sources":[],"favorites":[],"settings":[]}""",
            """{"version":2,"sources":["bad"],"favorites":[],"settings":{}}""",
            """{"version":2,"sources":[],"favorites":[{"key":"incomplete"}],"settings":{}}""",
        )
        bad.forEach { assertNull(it, BackupCodec.decode(it)) }
    }

    @Test fun `version two local source cannot silently lose payload`() {
        val json = """{"version":2,"sources":[{"id":"x","type":"FILE","location":"content://x/file"}],
            "favorites":[],"settings":{}}"""
        assertNull(BackupCodec.decode(json))
    }

    @Test fun `new backup without local files still round trips`() {
        val data = BackupData(emptyList(), emptyList(), BackupSettings(epgCustomUrl = "https://example.test/guide"))
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
    }
}
