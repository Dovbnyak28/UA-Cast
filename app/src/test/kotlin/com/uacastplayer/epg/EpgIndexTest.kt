package com.uacastplayer.epg

import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgIndexTest {

    private val bbcOne = EpgChannel(id = "bbc.one.uk", displayNames = listOf("BBC One", "BBC 1"), iconUrl = null)
    private val cnn = EpgChannel(id = "cnn.us", displayNames = listOf("CNN International"), iconUrl = null)
    private val index = EpgIndex(listOf(bbcOne, cnn))

    private fun channel(
        displayName: String,
        tvgId: String? = null,
        tvgName: String? = null,
    ) = M3uChannel(displayName = displayName, streamUrl = "http://example.com", tvgId = tvgId, tvgName = tvgName)

    @Test
    fun `matches by exact tvg-id`() {
        val result = index.match(channel("Anything", tvgId = "bbc.one.uk"))
        assertEquals(bbcOne, result)
    }

    @Test
    fun `matches by normalized tvg-id when case differs`() {
        val result = index.match(channel("Anything", tvgId = "BBC.ONE.UK"))
        assertEquals(bbcOne, result)
    }

    @Test
    fun `falls back to normalized tvg-name when tvg-id does not match`() {
        val result = index.match(channel("Anything", tvgId = "unknown", tvgName = "BBC One HD"))
        assertEquals(bbcOne, result)
    }

    @Test
    fun `falls back to normalized display name as a last resort`() {
        val result = index.match(channel("CNN International 4K"))
        assertEquals(cnn, result)
    }

    @Test
    fun `returns null when nothing matches`() {
        assertNull(index.match(channel("Totally Unknown Channel")))
    }

    @Test
    fun `prefers exact tvg-id over a name that would match a different channel`() {
        val result = index.match(channel("BBC One", tvgId = "cnn.us"))
        assertEquals(cnn, result)
    }
}
