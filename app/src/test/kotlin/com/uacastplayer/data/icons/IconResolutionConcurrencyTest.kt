package com.uacastplayer.data.icons

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IconResolutionConcurrencyTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun resetFixture() {
        application.getSharedPreferences("custom_icon_sources", Application.MODE_PRIVATE).edit().clear().commit()
        application.getSharedPreferences("uacast_icon_failures", Application.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun simultaneousRowsAndPrefetchRequestTheSameIconOnlyOnce() = resolveDuplicates("200 OK")

    @Test fun simultaneousMissesDoNotRepeatTheSameFailingRequest() = resolveDuplicates("503 Unavailable")

    @Test fun cancelledWaiterDoesNotCancelTheOwnersHttpRequest() = runBlocking {
        HeldIconOrigin().use { origin ->
            val repository = repository(origin)
            val owner = async(start = CoroutineStart.UNDISPATCHED) { repository.resolveIconFile("shared") }
            assertTrue(origin.firstStarted.await(5, TimeUnit.SECONDS))
            val waiter = async(start = CoroutineStart.UNDISPATCHED) { repository.resolveIconFile("shared") }
            waiter.cancelAndJoin()
            origin.releaseFirst.countDown()
            assertNotNull(owner.await())
            assertNotNull(repository.resolveIconFile("shared"))
            assertEquals(1, origin.requests.get())
        }
    }

    @Test fun cancelledOwnerLetsAnActiveWaiterRetryWithoutANegativeCacheEntry() = runBlocking {
        HeldIconOrigin(holdDuplicates = false).use { origin ->
            val repository = repository(origin)
            val owner = async(start = CoroutineStart.UNDISPATCHED) { repository.resolveIconFile("shared") }
            assertTrue(origin.firstStarted.await(5, TimeUnit.SECONDS))
            val waiter = async(start = CoroutineStart.UNDISPATCHED) { repository.resolveIconFile("shared") }
            owner.cancelAndJoin()
            assertNotNull(waiter.await())
            assertEquals(2, origin.requests.get())
        }
    }

    @Test fun unrelatedChannelDoesNotWaitForTheSlowIcon() = runBlocking {
        HeldIconOrigin().use { origin ->
            val repository = repository(origin)
            val slow = async(start = CoroutineStart.UNDISPATCHED) { repository.resolveIconFile("slow") }
            assertTrue(origin.firstStarted.await(5, TimeUnit.SECONDS))
            assertNotNull(repository.resolveIconFile("other"))
            assertEquals(1L, origin.releaseFirst.count)
            origin.releaseFirst.countDown()
            assertNotNull(slow.await())
            assertEquals(2, origin.requests.get())
        }
    }

    @Test fun obsoleteTransientFailureCannotReblockASuccessfulRetryAfterDiskEviction() =
        staleFailureAfterRetry("503 Unavailable")

    @Test fun obsoletePermanentFailureCannotReblockASuccessfulRetryAfterDiskEviction() =
        staleFailureAfterRetry("404 Not Found")

    private fun repository(origin: HeldIconOrigin): IconRepository = IconRepository(application).apply {
        addCustomIconSource(origin.base)
    }

    private fun resolveDuplicates(code: String) = runBlocking {
        HeldIconOrigin(code).use { origin ->
            val repository = repository(origin)
            val owner = async(start = CoroutineStart.UNDISPATCHED) { repository.resolveIconFile("shared") }
            assertTrue(origin.firstStarted.await(5, TimeUnit.SECONDS))
            // Each undispatched caller reaches the suspended resolve before any response is released.
            val waiters = List(7) {
                async(start = CoroutineStart.UNDISPATCHED) { repository.resolveIconFile("shared") }
            }
            // Signal-based observation window, not a sleep: an uncached duplicate exposes itself here.
            origin.secondStarted.await(500, TimeUnit.MILLISECONDS)
            origin.releaseFirst.countDown()
            val results = (listOf(owner) + waiters).awaitAll()
            if (code == "200 OK") results.forEach { assertNotNull(it) } else results.forEach { assertNull(it) }
            assertEquals("one generation must request a shared channel logo once", 1, origin.requests.get())
        }
    }

    private fun staleFailureAfterRetry(oldCode: String) = runBlocking {
        HeldIconOrigin(oldCode, holdDuplicates = false).use { origin ->
            val repository = repository(origin)
            val old = async(start = CoroutineStart.UNDISPATCHED) { repository.resolveIconFile("generation") }
            assertTrue(origin.firstStarted.await(5, TimeUnit.SECONDS))
            repository.retryTransientFailures()
            val fresh = repository.resolveIconFile("generation")
            assertNotNull("retry must finish independently of the obsolete HTTP request", fresh)
            origin.releaseFirst.countDown()
            assertNull(old.await())
            // Models an ordinary later LRU eviction: the memory cache is retired after removing its file.
            assertTrue(checkNotNull(fresh).delete())
            repository.invalidateMemoryCache()
            assertNotNull("a stale failure must not blacklist a URL already fetched successfully",
                repository.resolveIconFile("generation"))
            assertEquals(3, origin.requests.get())
        }
    }
}

/** Bounded local origin: only the first response (or shared duplicates) waits for an explicit release. */
private class HeldIconOrigin(private val firstCode: String = "200 OK",
    private val holdDuplicates: Boolean = true) : AutoCloseable {
    private val listener = ServerSocket(0)
    private val workers = Executors.newCachedThreadPool()
    val base = "http://127.0.0.1:${listener.localPort}"
    val requests = AtomicInteger()
    val firstStarted = CountDownLatch(1)
    val secondStarted = CountDownLatch(1)
    val releaseFirst = CountDownLatch(1)

    init {
        workers.execute {
            while (!listener.isClosed) {
                try {
                    val client = listener.accept()
                    workers.execute { serve(client) }
                } catch (_: IOException) { return@execute }
            }
        }
    }

    private fun serve(client: Socket) {
        try {
            client.use { socket ->
                socket.soTimeout = 5_000
                val reader = socket.getInputStream().bufferedReader()
                val line = reader.readLine()
                while (!reader.readLine().isNullOrEmpty()) { /* consume headers */ }
                val number = requests.incrementAndGet()
                if (number == 1) firstStarted.countDown() else secondStarted.countDown()
                val sharedDuplicate = holdDuplicates && line.contains("/shared.png")
                if (number == 1 || sharedDuplicate) check(releaseFirst.await(10, TimeUnit.SECONDS))
                val code = if (number == 1 || sharedDuplicate) firstCode else "200 OK"
                val body = if (code == "200 OK") {
                    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64)
                } else "fixture failure".toByteArray()
                val headers = "HTTP/1.1 $code\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().apply { write(headers.toByteArray()); write(body); flush() }
            }
        } catch (_: IOException) { /* owner cancellation deliberately closes a held socket */ }
    }

    override fun close() {
        releaseFirst.countDown()
        listener.close()
        workers.shutdownNow()
    }
}
