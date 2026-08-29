package com.uacastplayer.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgDataBuilderTest {

    @Test
    fun `groups programmes by channel and sorts each schedule`() {
        val channel = EpgChannel("one", listOf("One"), null)
        val late = programme("one", start = 300)
        val early = programme("one", start = 100)
        val other = programme("two", start = 200)

        val data = EpgDataBuilder.build(parsed(listOf(channel), listOf(late, other, early)))

        assertSame(channel, data.index.channels.single())
        assertEquals(listOf(early, late), data.programmesByChannelId.getValue("one"))
        assertEquals(listOf(other), data.programmesByChannelId.getValue("two"))
    }

    @Test
    fun `preserves parser truncation signals`() {
        val data = EpgDataBuilder.build(
            parsed(emptyList(), emptyList(), channelsDropped = true, programmesDropped = true),
        )

        assertTrue(data.truncation.channelsDropped)
        assertTrue(data.truncation.programmesDropped)
    }

    @Test
    fun `large build checks cancellation throughout the work`() {
        var checks = 0
        EpgDataBuilder.build(
            parsed(
                channels = emptyList(),
                programmes = List(600) { index -> programme("channel-${index % 3}", index.toLong()) },
            ),
            checkCancellation = { checks++ },
        )

        // At indices 0, 256 and 512, once per schedule group, and once before returning.
        assertEquals(7, checks)
    }

    private fun parsed(
        channels: List<EpgChannel>,
        programmes: List<EpgProgramme>,
        channelsDropped: Boolean = false,
        programmesDropped: Boolean = false,
    ) = XmlTvParseResult(channels, programmes, channelsDropped, programmesDropped)

    private fun programme(channelId: String, start: Long) = EpgProgramme(
        channelId = channelId,
        startMillis = start,
        stopMillis = start + 50,
        title = "Programme $start",
    )
}
