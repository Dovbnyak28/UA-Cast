package com.uacastplayer.home

import com.uacastplayer.playlist.M3uChannel
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeContentCancellationTest {
    @Test fun `cancellation is checked even when no last watched key exists`() {
        assertThrows(CancellationException::class.java) {
            HomeContentPolicy.resolve(null, emptyList(), emptyList()) { throw CancellationException("retired") }
        }
    }

    @Test fun `cancellation stops a missing-key scan within one channel batch`() {
        var reads = 0
        val channels = object : AbstractList<M3uChannel>() {
            override val size = 100_000
            override fun get(index: Int): M3uChannel {
                reads++
                return M3uChannel("Available", "https://unused.example.test/live", tvgId = "available")
            }
        }
        var probes = 0
        assertThrows(CancellationException::class.java) {
            HomeContentPolicy.resolve("missing", channels, emptyList()) {
                if (++probes == 3) throw CancellationException("superseded")
            }
        }
        assertEquals(257, reads)
    }
}
