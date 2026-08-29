package com.uacastplayer.data.playlist

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistCpuDispatcherTest {

    @Test
    fun `cancelled playlist work releases the single CPU lane promptly`() = runBlocking {
        val started = CountDownLatch(1)
        val obsolete = launch(start = CoroutineStart.UNDISPATCHED) {
            withPlaylistCpuCancellable { checkCancellation ->
                started.countDown()
                while (true) {
                    checkCancellation()
                    Thread.yield()
                }
            }
        }
        assertTrue("obsolete parse did not start", started.await(SETUP_TIMEOUT_SECONDS, TimeUnit.SECONDS))

        withTimeout(PLAYLIST_TIMEOUT_MILLIS) { obsolete.cancelAndJoin() }
        val result = withTimeout(PLAYLIST_TIMEOUT_MILLIS) { withPlaylistCpu { "replacement" } }

        assertEquals("replacement", result)
    }

    /**
     * Reproduces the device failure: non-cooperative player work occupies every Default worker.
     * Playlist CPU work must still start instead of sitting behind that unrelated backlog.
     */
    @Test
    fun `playlist work starts while the shared default pool is saturated`() {
        val parallelism = Runtime.getRuntime().availableProcessors().coerceAtLeast(MIN_DEFAULT_PARALLELISM)
        val started = CountDownLatch(parallelism)
        val release = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val blockers = List(parallelism) {
            scope.launch {
                started.countDown()
                release.await()
            }
        }

        try {
            assertTrue(
                "all Default workers should be occupied for this regression setup",
                started.await(SETUP_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )

            val result = runBlocking {
                withTimeout(PLAYLIST_TIMEOUT_MILLIS) {
                    withPlaylistCpu { "parsed" }
                }
            }

            assertEquals("parsed", result)
        } finally {
            release.countDown()
            runBlocking { blockers.joinAll() }
            scope.cancel()
        }
    }

    private companion object {
        const val MIN_DEFAULT_PARALLELISM = 2
        const val SETUP_TIMEOUT_SECONDS = 10L
        const val PLAYLIST_TIMEOUT_MILLIS = 2_000L
    }
}
