package com.uacastplayer.epg

import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgLookupTest {

    private val epgChannel = EpgChannel(id = "bbc.one.uk", displayNames = listOf("BBC One"), iconUrl = null)
    private val programmes = listOf(
        EpgProgramme("bbc.one.uk", 1000, 2000, "A", null),
        EpgProgramme("bbc.one.uk", 2000, 3000, "B", null),
    )
    private val data = EpgData(
        index = EpgIndex(listOf(epgChannel)),
        programmesByChannelId = mapOf("bbc.one.uk" to programmes),
    )

    @Test
    fun `resolves current programme for a matching channel`() {
        val channel = M3uChannel(displayName = "BBC One", streamUrl = "http://x", tvgId = "bbc.one.uk")
        val result = EpgLookup.currentAndNext(data, channel, nowMillis = 1500)
        assertEquals("A", result?.current?.title)
    }

    @Test
    fun `returns null when the channel does not match any EPG entry`() {
        val channel = M3uChannel(displayName = "Totally Unknown", streamUrl = "http://x")
        assertNull(EpgLookup.currentAndNext(data, channel, nowMillis = 1500))
    }

    @Test
    fun `returns null when the matched channel has no programmes`() {
        val noProgrammeChannel = EpgChannel(id = "empty.ch", displayNames = listOf("Empty Channel"), iconUrl = null)
        val dataWithNoProgrammes = EpgData(
            index = EpgIndex(listOf(noProgrammeChannel)),
            programmesByChannelId = emptyMap(),
        )
        val channel = M3uChannel(displayName = "Empty Channel", streamUrl = "http://x")
        assertNull(EpgLookup.currentAndNext(dataWithNoProgrammes, channel, nowMillis = 1500))
    }
}
