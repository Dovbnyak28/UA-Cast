package com.uacastplayer.playlist

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChannelSearchCancellationTest {
    @Test fun `cancellation is checked even for a blank query`() {
        assertThrows(CancellationException::class.java) {
            ChannelSearch.search(emptyList(), "  ") { throw CancellationException("retired") }
        }
    }

    @Test fun `cancelled scan stops within a channel batch`() {
        var reads = 0
        val channels = object : AbstractList<M3uChannel>() {
            override val size = 100_000
            override fun get(index: Int): M3uChannel {
                reads++
                return M3uChannel("Available", "https://unused.example.test/live")
            }
        }
        var probes = 0
        assertThrows(CancellationException::class.java) {
            ChannelSearch.search(listOf(group(channels)), "missing") {
                if (++probes == 4) throw CancellationException("superseded")
            }
        }
        assertEquals(257, reads)
    }

    @Test fun `cancellation is checked between empty groups`() {
        var reads = 0
        val groups = object : AbstractList<GroupedChannels>() {
            override val size = 100_000
            override fun get(index: Int): GroupedChannels {
                reads++
                return group(emptyList())
            }
        }
        var probes = 0
        assertThrows(CancellationException::class.java) {
            ChannelSearch.search(groups, "missing") {
                if (++probes == 3) throw CancellationException("superseded")
            }
        }
        assertEquals(2, reads)
    }

    @Test fun `cancellation is checked inside a long channel name`() {
        assertCancelledWithinName("a".repeat(16_384), "b")
    }

    @Test fun `cancellation is checked within a long matching prefix`() {
        assertCancelledWithinName("a".repeat(16_384), "a".repeat(8_192) + "b")
    }

    @Test fun `cancellation is checked within a long whitespace run`() {
        assertCancelledWithinName("a" + " ".repeat(16_384) + "z", "a z")
    }

    private fun assertCancelledWithinName(name: String, query: String) {
        var probes = 0
        assertThrows(CancellationException::class.java) {
            ChannelSearch.search(listOf(group(listOf(M3uChannel(name, "https://unused.example.test/live")))), query) {
                if (++probes == 5) throw CancellationException("retired inside name")
            }
        }
        assertEquals(5, probes)
    }

    private fun group(channels: List<M3uChannel>) = GroupedChannels(ChannelGroup.Custom("Test"), channels)
}
