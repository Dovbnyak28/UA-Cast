package com.uacastplayer.data.cast

import java.io.IOException
import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRestartAdmissionTest {
    @Test fun `finite request paused before admission cannot cross same token restart`() = Fixture().use { f ->
        f.holdSegment()
        f.restart("same-token")
        f.finishOld()
        assertEquals("retired request opened an origin in the restarted session", 0, f.segmentFetches.get())
        assertTrue(checkNotNull(f.oldCall).isCanceled())
    }

    @Test fun `finite request paused before admission cannot cross different token restart`() = Fixture().use { f ->
        f.holdSegment()
        f.restart("next-token")
        f.finishOld()
        assertEquals(0, f.segmentFetches.get())
        assertTrue(checkNotNull(f.oldCall).isCanceled())
    }

    @Test fun `stop alone rejects the paused finite request`() = Fixture().use { f ->
        f.holdSegment()
        f.server.stop()
        f.finishOld()
        assertEquals(0, f.segmentFetches.get())
        assertTrue(checkNotNull(f.oldCall).isCanceled())
    }

    @Test fun `channel handoff without restart still permits a promised finite segment`() = Fixture().use { f ->
        val socket = f.holdSegment()
        f.server.registerPlaylist("https://synthetic.test/next.m3u8")
        f.finishOld()
        assertEquals(1, f.segmentFetches.get())
        assertFalse(checkNotNull(f.oldCall).isCanceled())
        assertTrue(f.read(socket).endsWith("finite segment"))
    }

    @Test fun `retired admission cannot prevent a fresh segment in the restarted session`() = Fixture().use { f ->
        f.holdSegment()
        f.restart("same-token")
        f.finishOld()
        assertEquals(0, f.segmentFetches.get())
        assertTrue(f.fetchSegment().endsWith("finite segment"))
        assertEquals(1, f.segmentFetches.get())
    }

    private class Fixture : AutoCloseable {
        private val entered = CountDownLatch(1)
        private val resume = CountDownLatch(1)
        private val finished = CountDownLatch(1)
        private val firstSegment = AtomicBoolean(true)
        val segmentFetches = AtomicInteger()
        @Volatile var oldCall: Call? = null
        private val sockets = mutableListOf<Socket>()
        private val client = OkHttpClient.Builder()
            .eventListenerFactory { call ->
                if (call.request().url.encodedPath.endsWith(".ts") && firstSegment.compareAndSet(true, false)) {
                    oldCall = call
                    entered.countDown()
                    // A scheduler pause or listener initialization may span stop/start. Interrupting
                    // the old worker is not a guarantee that it has exited before start returns.
                    while (resume.count > 0) {
                        try { resume.await() } catch (_: InterruptedException) { /* Controlled pause. */ }
                    }
                    object : EventListener() {
                        override fun canceled(call: Call) { finished.countDown() }
                        override fun callEnd(call: Call) { finished.countDown() }
                        override fun callFailed(call: Call, ioe: IOException) { finished.countDown() }
                    }
                } else EventListener.NONE
            }
            .addInterceptor { chain ->
                val playlist = chain.request().url.encodedPath.endsWith(".m3u8")
                if (!playlist) segmentFetches.incrementAndGet()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .header("Content-Type", if (playlist) "application/vnd.apple.mpegurl" else "video/mp2t")
                    .body((if (playlist) MANIFEST else "finite segment").toResponseBody()).build()
            }.build()
        val server = ProxyServer(client)
        private var root = ""

        init { restart("same-token") }

        fun restart(token: String) {
            server.start(token, "127.0.0.1", remuxEnabled = false)
            root = server.registerPlaylist("https://synthetic.test/live.m3u8")
        }

        fun holdSegment(): Socket = request(segmentId()).also { assertTrue(entered.await(3, TimeUnit.SECONDS)) }

        fun finishOld() {
            resume.countDown()
            assertTrue("old request never reached cancellation or execution", finished.await(3, TimeUnit.SECONDS))
        }

        fun fetchSegment() = read(request(segmentId()))

        private fun segmentId(): String {
            assertTrue(read(request(root)).startsWith("HTTP/1.1 200"))
            return server.resourcesForTesting().entries.single { it.value.type == RESOURCE_TYPE_MEDIA }.key
        }

        private fun request(id: String): Socket {
            val uri = URI(server.buildLocalUrl(id))
            return Socket(uri.host, uri.port).also { socket ->
                sockets += socket
                socket.soTimeout = 3_000
                val request = "GET ${uri.rawPath} HTTP/1.1\r\nHost: localhost\r\n\r\n"
                socket.getOutputStream().write(request.toByteArray())
            }
        }

        fun read(socket: Socket) = socket.getInputStream().bufferedReader().readText()

        override fun close() {
            resume.countDown()
            server.stop()
            sockets.forEach(Socket::close)
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    private companion object {
        const val MANIFEST = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment.ts\n#EXT-X-ENDLIST\n"
    }
}
