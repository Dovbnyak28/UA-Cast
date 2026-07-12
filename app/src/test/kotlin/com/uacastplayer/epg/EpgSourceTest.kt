package com.uacastplayer.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgSourceTest {

    @Test
    fun `fromId resolves a known id`() {
        assertEquals(EpgSource.VARIANT_3, EpgSource.fromId("epg_one_3"))
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
    fun `all five variants use https`() {
        EpgSource.entries.forEach { source ->
            assertEquals(true, source.url.startsWith("https://"))
        }
    }
}
