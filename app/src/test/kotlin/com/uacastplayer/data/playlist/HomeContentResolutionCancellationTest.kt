package com.uacastplayer.data.playlist

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeContentResolutionCancellationTest {
    @Test fun `retired Home resolution releases the CPU lane without scanning the full playlist`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reads = AtomicInteger()
        val published = AtomicBoolean(false)
        val channels = object : AbstractList<M3uChannel>() {
            override val size = 100_000
            override fun get(index: Int): M3uChannel {
                if (reads.incrementAndGet() == 1) {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                }
                return CHANNEL
            }
        }
        val obsolete = launch(start = CoroutineStart.UNDISPATCHED) {
            resolveHomeContent("missing", channels, emptyList())
            published.set(true)
        }
        try {
            assertTrue("Home resolution never started", entered.await(10, TimeUnit.SECONDS))
            obsolete.cancel()
        } finally {
            release.countDown()
            withTimeout(5_000) { obsolete.cancelAndJoin() }
        }
        assertFalse("a cancelled owner published a result", published.get())
        assertTrue("retired resolution read ${reads.get()} channels", reads.get() in 1..257)
        println("Home retired-scan channelReads=${reads.get()}")
        val replacement = withTimeout(5_000) {
            resolveHomeContent("available", listOf(CHANNEL), emptyList())
        }
        assertSame(CHANNEL, replacement.continueWatching)
    }

    private companion object {
        val CHANNEL = M3uChannel("Available", "https://unused.example.test/live", tvgId = "available")
    }
}
