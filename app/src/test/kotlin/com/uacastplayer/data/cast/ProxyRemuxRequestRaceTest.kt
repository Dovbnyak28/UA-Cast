package com.uacastplayer.data.cast

import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRemuxRequestRaceTest {
    @Test fun `late raw response cannot replace selected channel`() = Fixture().use { f ->
        f.holdOldRequest()
        f.select("new.ts")
        f.assertOldRequestRejected()
    }

    @Test fun `late raw response cannot cross a proxy restart`() = Fixture().use { f ->
        f.holdOldRequest()
        f.server.start("new-token", "127.0.0.1")
        f.select("new.ts")
        f.assertOldRequestRejected()
    }

    @Test fun `same token and same channel after restart still reject old response`() = Fixture().use { f ->
        f.holdOldRequest()
        f.server.start("same-token", "127.0.0.1")
        f.select("old.ts")
        f.assertOldRequestRejected()
    }

    @Test fun `A B A cannot refresh ownership of an already fetched response`() = Fixture().use { f ->
        f.holdOldRequest()
        f.select("new.ts")
        f.select("old.ts")
        f.assertOldRequestRejected()
    }

    @Test fun `obsolete wrapper is rejected before opening its inner stream`() = Fixture(wrapper = true).use { f ->
        f.holdOldRequest()
        f.select("new.ts")
        f.assertOldRequestRejected()
    }

    @Test fun `stop without restart closes late raw response without a producer`() = Fixture().use { f ->
        f.holdOldRequest()
        f.server.stop()
        f.assertOldRequestRejected()
    }

    private class Fixture(private val wrapper: Boolean = false) : AutoCloseable {
        private val entered = CountDownLatch(1)
        private val resume = CountDownLatch(1)
        private val oldBodyClosed = CountDownLatch(1)
        private val firstOldBody = AtomicBoolean(true)
        private val firstOldRoute = AtomicBoolean(true)
        private var oldId = ""
        private var oldSocket: Socket? = null
        private val sockets = mutableListOf<Socket>()
        private val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val isWrapper = request.url.encodedPath.endsWith("wrapper.m3u8")
            val body = if (isWrapper) {
                "#EXTM3U\n#EXTINF:-1,Stream\nold.ts\n".toResponseBody()
            } else if (request.url.encodedPath.endsWith("old.ts") && firstOldBody.compareAndSet(true, false)) {
                ClosingBody(oldBodyClosed)
            } else RAW_TS.toResponseBody()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", if (isWrapper) "application/vnd.apple.mpegurl" else "video/mp2t")
                .body(body).build()
        }.build()
        val server = ProxyServer(http) { id, _ ->
            if (id == oldId && firstOldRoute.compareAndSet(true, false)) {
                entered.countDown()
                var interrupted = false
                while (resume.count > 0) {
                    try { resume.await() } catch (_: InterruptedException) { interrupted = true }
                }
                if (interrupted) Thread.currentThread().interrupt()
            }
        }
        private val registry = field(server, "resourceRegistry") as ProxyResourceRegistry
        private val remuxLock = field(registry, "remuxLock")

        init { server.start("same-token", "127.0.0.1") }

        fun holdOldRequest() {
            oldId = server.registerPlaylist("https://synthetic.test/" + if (wrapper) "wrapper.m3u8" else "old.ts")
            oldSocket = request(oldId)
            assertTrue("old request did not reach route boundary", entered.await(3, TimeUnit.SECONDS))
        }

        fun select(path: String) {
            val id = server.registerPlaylist("https://synthetic.test/$path")
            request(id)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (active()?.resourceId != id && System.nanoTime() < deadline) Thread.sleep(5)
            assertTrue("selected channel did not get its producer", active()?.resourceId == id)
        }

        fun assertOldRequestRejected() {
            val expected = active()
            resume.countDown()
            // Restart closes the old socket outright. Otherwise the rejected late request gets a
            // complete bounded error, never an HLS manifest from a stale producer.
            val socket = checkNotNull(oldSocket)
            val response = socket.getInputStream().bufferedReader().readText()
            assertTrue("stale request unexpectedly succeeded: $response",
                response.isEmpty() || response.startsWith("HTTP/1.1 503"))
            if (wrapper) {
                assertTrue("obsolete wrapper opened an inner origin", firstOldBody.get())
            } else {
                assertTrue("obsolete response was not closed", oldBodyClosed.await(3, TimeUnit.SECONDS))
            }
            assertSame("obsolete request changed the active producer", expected, active())
        }

        private fun active(): RawTsRemuxSession? = synchronized(remuxLock) {
            nullableField(registry, "activeRemuxSession") as? RawTsRemuxSession
        }

        private fun request(id: String): Socket {
            val uri = URI(server.buildLocalUrl(id))
            return Socket(uri.host, uri.port).also { socket ->
                sockets += socket
                socket.soTimeout = 3_000
                socket.getOutputStream().write("GET ${uri.rawPath} HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
            }
        }

        override fun close() {
            resume.countDown()
            server.stop()
            sockets.forEach(Socket::close)
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdownNow()
        }
    }

    private class ClosingBody(private val closed: CountDownLatch) : ResponseBody() {
        private val bytes = object : ForwardingSource(Buffer().write(RAW_TS)) {
            override fun close() {
                super.close()
                closed.countDown()
            }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = RAW_TS.size.toLong()
        override fun source() = bytes
    }

    private companion object {
        val RAW_TS = ByteArray(188 * 800) { offset ->
            when (offset % 188) { 0 -> 0x47; 1 -> 0x1f; 2 -> 0xff; 3 -> 0x10; else -> 0 }.toByte()
        }
        fun nullableField(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply {
            isAccessible = true
        }.get(owner)
        fun field(owner: Any, name: String): Any = checkNotNull(nullableField(owner, name))
    }
}
