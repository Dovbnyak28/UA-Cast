package com.uacastplayer.data.cast

import java.io.IOException
import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyFlattenedOwnershipTest {
    @Test fun `channel switch cancels old flattened segment without waiting for renderer Stop`() =
        Fixture(blockSegment = true).use { f ->
            f.requestOld()
            f.awaitSegment()
            f.select("new")
            assertTrue("old upstream remained alive after channel switch", f.cancelled.await(1, TimeUnit.SECONDS))
        }

    @Test fun `same channel registration preserves active flattened response`() =
        Fixture(blockSegment = true).use { f ->
            f.requestOld()
            f.awaitSegment()
            f.select("old")
            assertFalse(checkNotNull(f.segmentCall).isCanceled())
            f.releaseSegment.set(true)
            assertTrue(f.readOldResponse().startsWith("HTTP/1.1 200"))
        }

    @Test fun `late manifest cannot start flattened producer for previous channel`() =
        Fixture(holdRoute = true).use { f ->
            f.holdOldRequest()
            f.select("new")
            f.assertRejectedWithoutReplay()
        }

    @Test fun `A B A does not renew a late flattened request`() = Fixture(holdRoute = true).use { f ->
        f.holdOldRequest()
        f.select("new")
        f.select("old")
        f.assertRejectedWithoutReplay()
    }

    @Test fun `same token restart cannot admit old flattened request into new session`() =
        Fixture(holdRoute = true).use { f ->
            f.holdOldRequest()
            f.restart()
            f.select("old")
            f.assertRejectedWithoutReplay()
        }

    @Test fun `old channel request cannot create new flattened producer after switch`() = Fixture().use { f ->
        f.select("new")
        f.requestOld()
        f.assertRejectedWithoutReplay()
    }

    private class Fixture(
        private val holdRoute: Boolean = false,
        private val blockSegment: Boolean = false,
    ) : AutoCloseable {
        private val entered = CountDownLatch(1)
        private val resume = CountDownLatch(1)
        private val segmentEntered = CountDownLatch(1)
        private val routeFinished = CountDownLatch(1)
        private val firstRoute = AtomicBoolean(true)
        val cancelled = CountDownLatch(1)
        val releaseSegment = AtomicBoolean()
        val manifestFetches = AtomicInteger()
        @Volatile var segmentCall: Call? = null
        private var socket: Socket? = null
        private var responsePool: ThreadPoolExecutor? = null
        private val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val manifest = request.url.encodedPath.endsWith(".m3u8")
            val bytes = if (manifest) {
                manifestFetches.incrementAndGet()
                PLAYLIST.toByteArray()
            } else {
                segmentCall = chain.call()
                segmentEntered.countDown()
                while (blockSegment && !releaseSegment.get() && !chain.call().isCanceled()) Thread.sleep(5)
                if (chain.call().isCanceled()) {
                    cancelled.countDown()
                    throw IOException("cancelled synthetic segment")
                }
                TS_BYTES
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", if (manifest) "application/vnd.apple.mpegurl" else "video/mp2t")
                .body(bytes.toResponseBody()).build()
        }.build()
        val server = ProxyServer(http) { _, _ ->
            if (holdRoute && firstRoute.compareAndSet(true, false)) {
                entered.countDown()
                while (resume.count > 0) {
                    try { resume.await() } catch (_: InterruptedException) { /* Delayed callback across restart. */ }
                }
                routeFinished.countDown()
            }
        }
        private val oldId: String

        init {
            restart()
            oldId = select("old")
        }

        fun restart() { server.start("same-token", "127.0.0.1", remuxEnabled = false, flattenHlsToStream = true) }
        fun select(name: String): String = server.registerPlaylist("https://synthetic.test/$name.m3u8")
        fun requestOld() {
            responsePool = field(field(server, "httpServer"), "executor") as ThreadPoolExecutor
            val uri = URI(server.buildLocalUrl(oldId))
            socket = Socket(uri.host, uri.port).apply {
                soTimeout = 3_000
                getOutputStream().write("GET ${uri.rawPath} HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
            }
        }

        fun holdOldRequest() {
            requestOld()
            assertTrue("old route did not reach barrier", entered.await(3, TimeUnit.SECONDS))
        }

        fun awaitSegment() { assertTrue(segmentEntered.await(3, TimeUnit.SECONDS)) }
        fun readOldResponse(): String = checkNotNull(socket).getInputStream().bufferedReader().readText()
        fun assertRejectedWithoutReplay() {
            resume.countDown()
            if (holdRoute) assertTrue(routeFinished.await(3, TimeUnit.SECONDS))
            val response = readOldResponse()
            // A restart already closed the old socket; otherwise return a bounded 503.
            assertTrue("obsolete request succeeded: ${response.take(100)}",
                response.isEmpty() || response.startsWith("HTTP/1.1 503"))
            // EOF on a restarted socket can precede its old handler's cleanup. Observe that exact
            // worker generation, not the new pool or a set cleared by stop().
            val pool = checkNotNull(responsePool)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (pool.activeCount != 0 && System.nanoTime() < deadline) Thread.sleep(5)
            assertEquals("old response worker did not finish", 0, pool.activeCount)
            // A late response already fetched its original manifest. A newly arriving stale URL
            // is now rejected at Call admission, before even that first fetch.
            assertEquals("obsolete request opened an origin", if (holdRoute) 1 else 0, manifestFetches.get())
        }

        override fun close() {
            resume.countDown()
            releaseSegment.set(true)
            server.stop()
            socket?.close()
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdownNow()
        }
    }

    private companion object {
        const val PLAYLIST = "#EXTM3U\n#EXT-X-TARGETDURATION:1\n#EXTINF:1,\nsegment.ts\n#EXT-X-ENDLIST\n"
        val TS_BYTES = ByteArray(188 * 4) { if (it % 188 == 0) 0x47.toByte() else 0 }
        fun field(owner: Any, name: String): Any = checkNotNull(owner.javaClass.getDeclaredField(name).apply {
            isAccessible = true
        }.get(owner))
    }
}
