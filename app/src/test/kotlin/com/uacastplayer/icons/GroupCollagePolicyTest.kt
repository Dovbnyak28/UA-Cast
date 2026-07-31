package com.uacastplayer.icons

import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupCollagePolicyTest {

    private fun channel(name: String, tvgLogo: String? = null, tvgId: String? = null) =
        M3uChannel(displayName = name, streamUrl = "https://example.com/$name", tvgLogo = tvgLogo, tvgId = tvgId)

    @Test
    fun `channels with neither tvgLogo nor tvgId are excluded`() {
        val channels = listOf(channel("a"), channel("b", tvgLogo = "logo.png"))

        assertEquals(listOf(channels[1]), GroupCollagePolicy.candidateChannels(channels))
    }

    @Test
    fun `a channel with only tvgId still counts as a candidate`() {
        val channels = listOf(channel("a", tvgId = "id1"))

        assertEquals(channels, GroupCollagePolicy.candidateChannels(channels))
    }

    @Test
    fun `caps at MAX_TILES even when more channels qualify`() {
        val channels = (1..10).map { channel("ch$it", tvgLogo = "logo$it.png") }

        val result = GroupCollagePolicy.candidateChannels(channels)

        assertEquals(GroupCollagePolicy.MAX_TILES, result.size)
        assertEquals(channels.take(GroupCollagePolicy.MAX_TILES), result)
    }

    @Test
    fun `order is deterministic and matches the group's own channel order`() {
        val channels = listOf(
            channel("third", tvgLogo = "3.png"),
            channel("first", tvgLogo = "1.png"),
            channel("second", tvgLogo = "2.png"),
        )

        assertEquals(channels, GroupCollagePolicy.candidateChannels(channels))
    }

    @Test
    fun `empty channel list yields no candidates`() {
        assertEquals(emptyList<M3uChannel>(), GroupCollagePolicy.candidateChannels(emptyList()))
    }
}
