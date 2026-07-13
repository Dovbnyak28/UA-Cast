package com.uacastplayer.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgSourceTest {

    @Test
    fun `fromId resolves a known id`() {
        assertEquals(EpgSource.PERFECT_PLAYER, EpgSource.fromId("epg_it999_pp"))
    }

    @Test
    fun `fromId falls back to default for null`() {
        assertEquals(EpgSource.DEFAULT, EpgSource.fromId(null))
    }

    @Test
    fun `fromId falls back to default for an unknown id`() {
        assertEquals(EpgSource.DEFAULT, EpgSource.fromId("nonexistent"))
    }

    @Test
    fun `all five sources use https`() {
        EpgSource.entries.forEach { source ->
            assertTrue(source.url.startsWith("https://"))
        }
    }

    @Test
    fun `every source has a distinct id and url`() {
        assertEquals(EpgSource.entries.size, EpgSource.entries.map { it.id }.distinct().size)
        assertEquals(EpgSource.entries.size, EpgSource.entries.map { it.url }.distinct().size)
    }
}
