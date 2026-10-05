package com.uacastplayer.data.epg

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

class EpgCpuDispatcherTest {

    @Test
    fun `cancelled EPG work releases the single CPU lane promptly`() = runBlocking {
        val started = CountDownLatch(1)
        val obsolete = launch(start = CoroutineStart.UNDISPATCHED) {
            withEpgCpuCancellable { checkCancellation ->
                started.countDown()
                while (true) {
                    checkCancellation()
                    Thread.yield()
                }
            }
        }
        assertTrue("obsolete parse did not start", started.await(SETUP_TIMEOUT_SECONDS, TimeUnit.SECONDS))

        withTimeout(EPG_TIMEOUT_MILLIS) { obsolete.cancelAndJoin() }
        val result = withTimeout(EPG_TIMEOUT_MILLIS) { withEpgCpu { "replacement" } }

        assertEquals("replacement", result)
    }

    @Test
    fun `EPG parsing starts while the shared default pool is saturated`() {
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
                withTimeout(EPG_TIMEOUT_MILLIS) {
                    withEpgCpu { "indexed" }
                }
            }

            assertEquals("indexed", result)
        } finally {
            release.countDown()
            runBlocking { blockers.joinAll() }
            scope.cancel()
        }
    }

    private companion object {
        const val MIN_DEFAULT_PARALLELISM = 2
        const val SETUP_TIMEOUT_SECONDS = 10L
        const val EPG_TIMEOUT_MILLIS = 2_000L
    }
}
