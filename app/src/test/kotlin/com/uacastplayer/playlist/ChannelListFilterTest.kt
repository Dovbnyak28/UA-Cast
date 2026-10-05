package com.uacastplayer.playlist

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ChannelListFilterTest {
    @Test fun `unfiltered hundred thousand channels are reused without scanning or copying`() {
        val channels = CountingChannels()
        assertSame(channels, ChannelListFilter.filter(channels, emptyList(), emptySet(), "  "))
        assertEquals(0, channels.reads)
    }

    @Test fun `search preserves order duplicate items unicode and case insensitive matching`() {
        val a = channel("Новини España")
        val b = channel("Спорт")
        val channels = listOf(a, b, a)
        assertEquals(listOf(a, a), ChannelListFilter.filter(channels, emptyList(), emptySet(), "  НОВИНИ "))
        assertEquals(listOf(a, a), ChannelListFilter.filter(channels, emptyList(), emptySet(), "ESPAÑA"))
    }

    @Test fun `hidden groups and search are filtered together in group playback order`() {
        val hidden = ChannelGroup.Custom("Hidden")
        val visible = ChannelGroup.Known(ChannelGroup.KEY_NEWS)
        val a = channel("News visible")
        val b = channel("News hidden")
        val groups = listOf(GroupedChannels(hidden, listOf(b)), GroupedChannels(visible, listOf(a, a)))
        assertEquals(listOf(a, a), ChannelListFilter.filter(listOf(b, a, a), groups,
            setOf(groupDisplayKey(hidden)), "news"))
    }

    @Test fun `cancelled search stops after at most one interval instead of scanning the whole playlist`() {
        val channels = CountingChannels()
        var probes = 0
        assertThrows(CancellationException::class.java) {
            ChannelListFilter.filter(channels, emptyList(), emptySet(), "no-match") {
                if (++probes == 3) throw CancellationException("superseded")
            }
        }
        assertEquals(257, channels.reads)
    }

    @Test fun `cancelled unfiltered request is checked before returning its reused list`() {
        assertThrows(CancellationException::class.java) {
            ChannelListFilter.filter(emptyList(), emptyList(), emptySet(), "") {
                throw CancellationException("cancelled")
            }
        }
    }

    private class CountingChannels : AbstractList<M3uChannel>() {
        override val size: Int = 100_000
        var reads = 0
        override fun get(index: Int): M3uChannel {
            reads++
            return channel("Channel $index")
        }
    }

    private companion object {
        fun channel(name: String) = M3uChannel(name, "https://unused.example.test/live")
    }
}
