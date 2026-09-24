package com.uacastplayer.data.cast

import java.io.IOException
import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyUpstreamOwnershipTest {
    @Test fun `switch cancels a direct live TS response already being copied`() = Fixture().use { f ->
        f.beginLive()
        f.select("new.ts")
        assertTrue("old direct Call was not cancelled", checkNotNull(f.liveCall).isCanceled())
    }

    @Test fun `switch cancels unwrapped live TS under its original root owner`() = Fixture(wrapper = true).use { f ->
        f.beginLive()
        f.select("new.ts")
        assertTrue("old unwrapped Call was not cancelled", checkNotNull(f.liveCall).isCanceled())
    }

    @Test fun `same root registration does not cancel a live TS response`() = Fixture().use { f ->
        f.beginLive()
        f.select("old.ts")
        assertFalse(checkNotNull(f.liveCall).isCanceled())
    }

    @Test fun `switch cancels root fetch still waiting for headers`() = Fixture(waitHeaders = true).use { f ->
        f.request(f.rootId)
        assertTrue(f.callEntered.await(3, TimeUnit.SECONDS))
        f.select("new.ts")
        assertTrue("pending root fetch survived channel change", checkNotNull(f.liveCall).isCanceled())
    }

    @Test fun `stale root URL is rejected before another origin request`() = Fixture().use { f ->
        f.select("new.ts")
        assertTrue(f.response(f.rootId).startsWith("HTTP/1.1 503"))
        assertEquals(0, f.originRequests.get())
    }

    @Test fun `late wrapper cannot open inner origin after A B A`() =
        Fixture(wrapper = true, holdRoute = true).use { f ->
            val socket = f.holdOld()
            f.select("new.ts")
            f.select("wrapper.m3u8")
            f.resume.countDown()
            assertTrue(f.read(socket).startsWith("HTTP/1.1 503"))
            assertEquals("obsolete wrapper opened its inner stream", 1, f.originRequests.get())
        }

    @Test fun `late plain manifest cannot publish into a renewed A B A root`() =
        Fixture(manifest = true, holdRoute = true).use { f ->
            val socket = f.holdOld()
            f.select("new.ts")
            f.select("old.m3u8")
            f.resume.countDown()
            assertTrue(f.read(socket).startsWith("HTTP/1.1 503"))
            assertEquals(0, f.server.resourcesForTesting().values.count { it.type == RESOURCE_TYPE_MEDIA })
        }

    @Test fun `manifest delayed after routing still cannot commit across A B A`() =
        Fixture(manifest = true, holdBody = true).use { f ->
            val socket = f.holdManifestRead()
            f.select("new.ts")
            f.select("old.m3u8")
            f.resume.countDown()
            assertTrue(f.read(socket).startsWith("HTTP/1.1 503"))
            assertEquals(0, f.server.resourcesForTesting().values.count { it.type == RESOURCE_TYPE_MEDIA })
        }

    @Test fun `in flight finite segment remains allowed to drain across channel switch`() =
        Fixture(manifest = true, waitHeaders = true).use { f ->
            val segment = f.publishSegment()
            val socket = f.request(segment)
            assertTrue(f.callEntered.await(3, TimeUnit.SECONDS))
            f.select("new.ts")
            assertFalse("finite promised segment was cancelled", checkNotNull(f.liveCall).isCanceled())
            f.releaseHeaders.set(true)
            assertTrue(f.read(socket).endsWith("finite segment"))
        }

    @Test fun `previously promised finite segment can still be requested after switch`() =
        Fixture(manifest = true).use { f ->
            val segment = f.publishSegment()
            f.select("new.ts")
            assertTrue(f.response(segment).endsWith("finite segment"))
        }

    @Test fun `stop also cancels an old finite segment retained for handoff`() =
        Fixture(manifest = true, waitHeaders = true).use { f ->
            f.request(f.publishSegment())
            assertTrue(f.callEntered.await(3, TimeUnit.SECONDS))
            f.select("new.ts")
            assertFalse(checkNotNull(f.liveCall).isCanceled())
            f.server.stop()
            assertTrue(checkNotNull(f.liveCall).isCanceled())
        }

    private class Fixture(
        private val wrapper: Boolean = false,
        private val manifest: Boolean = false,
        private val waitHeaders: Boolean = false,
        private val holdRoute: Boolean = false,
        private val holdBody: Boolean = false,
    ) : AutoCloseable {
        val callEntered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val releaseHeaders = AtomicBoolean()
        val originRequests = AtomicInteger()
        @Volatile var liveCall: Call? = null
        private val bodyBlocked = CountDownLatch(1)
        private val routeEntered = CountDownLatch(1)
        private val firstRoute = AtomicBoolean(true)
        private val sockets = mutableListOf<Socket>()
        private val http = OkHttpClient.Builder().addInterceptor { chain ->
            originRequests.incrementAndGet()
            val request = chain.request()
            val path = request.url.encodedPath
            val body: ResponseBody = when {
                path.endsWith("wrapper.m3u8") -> WRAPPER.toResponseBody()
                path.endsWith(".m3u8") ->
                    if (holdBody) DelayedManifest(bodyBlocked, resume) else MANIFEST.toResponseBody()
                else -> {
                    liveCall = chain.call()
                    callEntered.countDown()
                    while (waitHeaders && !releaseHeaders.get() && !chain.call().isCanceled()) {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5))
                    }
                    if (chain.call().isCanceled()) throw IOException("cancelled synthetic origin")
                    if (manifest) "finite segment".toResponseBody() else LiveBody(chain.call(), bodyBlocked)
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", if (path.endsWith(".m3u8")) "application/vnd.apple.mpegurl" else "video/mp2t")
                .body(body).build()
        }.build()
        val server = ProxyServer(http) { _, _ ->
            if (holdRoute && firstRoute.compareAndSet(true, false)) {
                routeEntered.countDown()
                while (resume.count > 0) {
                    try { resume.await() } catch (_: InterruptedException) { /* Controlled delayed callback. */ }
                }
            }
        }
        val rootId: String

        init {
            server.start("session", "127.0.0.1", remuxEnabled = false)
            rootId = select(when { wrapper -> "wrapper.m3u8"; manifest -> "old.m3u8"; else -> "old.ts" })
        }

        fun select(path: String) = server.registerPlaylist("https://synthetic.test/$path")
        fun request(id: String): Socket {
            val uri = URI(server.buildLocalUrl(id))
            return Socket(uri.host, uri.port).also { socket ->
                sockets += socket
                socket.soTimeout = 3_000
                socket.getOutputStream().write("GET ${uri.rawPath} HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
            }
        }

        fun read(socket: Socket) = socket.getInputStream().bufferedReader().readText()
        fun response(id: String) = read(request(id))
        fun beginLive() {
            request(rootId)
            assertTrue("proxy never began copying the live body", bodyBlocked.await(3, TimeUnit.SECONDS))
        }

        fun holdOld(): Socket = request(rootId).also { assertTrue(routeEntered.await(3, TimeUnit.SECONDS)) }
        fun holdManifestRead(): Socket = request(rootId).also { assertTrue(bodyBlocked.await(3, TimeUnit.SECONDS)) }
        fun publishSegment(): String {
            assertTrue(response(rootId).startsWith("HTTP/1.1 200"))
            return server.resourcesForTesting().entries.single { it.value.type == RESOURCE_TYPE_MEDIA }.key
        }

        override fun close() {
            resume.countDown()
            releaseHeaders.set(true)
            server.stop()
            sockets.forEach(Socket::close)
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdownNow()
        }
    }

    /** Infinite origin until Call cancellation, after enough TS bytes for the real route sniffer. */
    private class LiveBody(call: Call, blocked: CountDownLatch) : ResponseBody() {
        private val source = object : Source {
            private val initial = Buffer().write(TS_BYTES)
            private val closed = AtomicBoolean()
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (initial.size > 0) return initial.read(sink, byteCount)
                blocked.countDown()
                while (!closed.get() && !call.isCanceled()) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5))
                throw IOException("synthetic origin closed")
            }
            override fun close() { closed.set(true) }
            override fun timeout() = Timeout.NONE
        }.buffer()
        override fun contentLength() = -1L
        override fun contentType(): MediaType? = null
        override fun source() = source
    }

    /** Pause after the 16-byte route sniff, before publishing the manifest graph. */
    private class DelayedManifest(entered: CountDownLatch, resume: CountDownLatch) : ResponseBody() {
        private val source = object : Source {
            private val body = Buffer().writeUtf8(MANIFEST)
            private var sniffed = false
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (!sniffed) {
                    sniffed = true
                    return body.read(sink, minOf(16, byteCount))
                }
                entered.countDown()
                // The bytes may already be buffered when Call.cancel() occurs; publication must
                // validate ownership independently of a future socket read throwing.
                resume.await(3, TimeUnit.SECONDS)
                return body.read(sink, byteCount)
            }
            override fun close() = Unit
            override fun timeout() = Timeout.NONE
        }.buffer()
        override fun contentLength() = MANIFEST.length.toLong()
        override fun contentType(): MediaType? = null
        override fun source() = source
    }

    private companion object {
        const val WRAPPER = "#EXTM3U\n#EXTINF:-1,Channel\nold.ts\n"
        const val MANIFEST = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment.ts\n#EXT-X-ENDLIST\n"
        val TS_BYTES = ByteArray(188 * 800) { if (it % 188 == 0) 0x47.toByte() else 0 }
    }
}
