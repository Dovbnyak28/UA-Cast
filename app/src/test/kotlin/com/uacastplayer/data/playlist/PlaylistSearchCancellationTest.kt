package com.uacastplayer.data.playlist

import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.ChannelSearchOutcome
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSearchCancellationTest {
    @Test fun `cancelled global search stops scanning and permits replacement on the CPU lane`() {
        assertCancellation { channels ->
            searchPlaylistChannels(listOf(GroupedChannels(GROUP, channels)), "missing")
        }
    }

    @Test fun `cancelled single group filter stops scanning and permits replacement on the CPU lane`() {
        assertCancellation { channels -> filterPlaylistChannels(channels, "missing") }
    }

    @Test fun `blank group filter reuses the channel list without scanning`() = runBlocking {
        val channels = CountingChannels()
        assertSame(channels, filterPlaylistChannels(channels, ""))
        assertEquals(0, channels.reads.get())
    }

    private fun assertCancellation(search: suspend (List<M3uChannel>) -> Any) = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val published = AtomicBoolean(false)
        val channels = CountingChannels {
            entered.countDown()
            check(release.await(SETUP_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
        val obsolete = launch(start = CoroutineStart.UNDISPATCHED) {
            search(channels)
            published.set(true)
        }
        try {
            assertTrue("the scan never started", entered.await(SETUP_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            obsolete.cancel()
        } finally {
            release.countDown()
            withTimeout(COMPLETION_TIMEOUT_MILLIS) { obsolete.cancelAndJoin() }
        }
        assertFalse("a cancelled request published its result", published.get())
        assertTrue("cancelled work read ${channels.reads.get()} channels",
            channels.reads.get() in 1..MAX_CANCELLED_READS)
        val replacement = withTimeout(COMPLETION_TIMEOUT_MILLIS) {
            searchPlaylistChannels(listOf(GroupedChannels(GROUP, listOf(CHANNEL))), "available")
        }
        assertEquals(listOf(CHANNEL), (replacement as ChannelSearchOutcome.Matches).results.map { it.channel })
    }

    private class CountingChannels(private val onFirstRead: () -> Unit = {}) : AbstractList<M3uChannel>() {
        override val size: Int = 100_000
        val reads = AtomicInteger()
        override fun get(index: Int): M3uChannel {
            if (reads.incrementAndGet() == 1) onFirstRead()
            return CHANNEL
        }
    }

    private companion object {
        val GROUP = ChannelGroup.Custom("Test")
        val CHANNEL = M3uChannel("Available", "https://unused.example.test/live")
        const val MAX_CANCELLED_READS = 257
        const val SETUP_TIMEOUT_SECONDS = 10L
        const val COMPLETION_TIMEOUT_MILLIS = 5_000L
    }
}
