package com.uacastplayer.playlist

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizedChannelQueryTest {
    @Test fun `a query longer than the raw name needs no character scan`() {
        val matcher = NormalizedChannelQuery("a".repeat(8_192) + "b") {}
        var probes = 0
        assertFalse(matcher.contains("a".repeat(8_192)) { probes++ })
        assertEquals("Source normalization cannot increase its length", 0, probes)
    }

    @Test fun `cancellation stops prefix compilation before channels are scanned`() {
        var reads = 0
        val channels = object : AbstractList<M3uChannel>() {
            override val size = 1
            override fun get(index: Int): M3uChannel {
                reads++
                return M3uChannel("Unused", "https://unused.example.test/live")
            }
        }
        var probes = 0
        assertThrows(CancellationException::class.java) {
            ChannelSearch.search(listOf(GroupedChannels(ChannelGroup.Custom("Test"), channels)), "a".repeat(8_192)) {
                if (++probes == 3) throw CancellationException("cancelled during query compilation")
            }
        }
        assertEquals(0, reads)
        assertEquals(3, probes)
    }

    @Test fun `cancellation is checked while falling back through odd length prefixes`() {
        val repeatedPrefix = "a" + "ba".repeat(8_192)
        val matcher = NormalizedChannelQuery(repeatedPrefix + "c") {}
        var probes = 0
        assertThrows(CancellationException::class.java) {
            matcher.contains(repeatedPrefix + "d") {
                // 17 raw-character checkpoints precede the final mismatch; subsequent checks
                // must happen inside the long fallback, whose prefix lengths skip multiples of 1024.
                if (++probes == 18) throw CancellationException("cancelled during fallback")
            }
        }
        assertEquals(18, probes)
    }

    @Test fun `matching state belongs to the name scan not the shared query`() {
        val matcher = NormalizedChannelQuery("ababc") {}
        assertFalse(matcher.contains("abab") {})
        assertFalse(matcher.contains("c") {})
        assertTrue(matcher.contains("ababc") {})
        assertFalse(matcher.contains("") {})
        assertTrue(matcher.contains("ABABC") {})
    }
}
