package com.uacastplayer.data.cast

import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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

/** Real proxy sockets with deterministic failures at the origin body, not timing-based sleeps. */
class ProxyProbeFailureTest {
    @Test fun `disabled remux answers direct HEAD without waiting for the codec probe`() =
        Fixture(initialBytes = 8_192).use { f ->
            assertTrue(f.request("HEAD").startsWith("HTTP/1.1 200"))
            assertEquals("disabled remux read beyond the short format sniff", 1, f.body.reads.get())
            assertTrue(f.body.closed.get())
        }

    @Test fun `disabled remux answers wrapper HEAD without reading the live body`() =
        Fixture(wrapper = true, initialBytes = 0).use { f ->
            assertTrue(f.request("HEAD").startsWith("HTTP/1.1 200"))
            assertEquals(0, f.body.reads.get())
            assertTrue(f.body.closed.get())
        }

    @Test fun `reset during the short format sniff returns 502 and closes upstream`() =
        Fixture(initialBytes = 0, failure = IOException("synthetic reset")).use { f ->
            assertTrue(f.request("GET").startsWith("HTTP/1.1 502"))
            assertTrue(f.body.closed.get())
        }

    @Test fun `timeout during enabled codec probing returns 502 and closes upstream`() =
        Fixture(initialBytes = 8_192, remux = true).use { f ->
            assertTrue(f.request("GET").startsWith("HTTP/1.1 502"))
            assertTrue(f.body.closed.get())
        }

    @Test fun `unwrapped codec probe timeout returns 502 and closes inner upstream`() =
        Fixture(wrapper = true, initialBytes = 0, remux = true).use { f ->
            assertTrue(f.request("HEAD").startsWith("HTTP/1.1 502"))
            assertTrue(f.body.closed.get())
        }

    @Test fun `forbidden unwrapped response is not probed or remuxed`() =
        Fixture(wrapper = true, initialBytes = 0, remux = true, code = 403).use { f ->
            assertTrue(f.request("HEAD").startsWith("HTTP/1.1 403"))
            assertEquals(0, f.body.reads.get())
            assertTrue(f.body.closed.get())
        }

    @Test fun `missing unwrapped response preserves the origin status without probing`() =
        Fixture(wrapper = true, initialBytes = 0, remux = true, code = 404).use { f ->
            assertTrue(f.request("HEAD").startsWith("HTTP/1.1 404"))
            assertEquals(0, f.body.reads.get())
            assertTrue(f.body.closed.get())
        }

    @Test fun `a failed probe does not prevent a subsequent healthy channel request`() =
        Fixture(initialBytes = 0).use { f ->
            assertTrue(f.request("GET").startsWith("HTTP/1.1 502"))
            val response = f.request("GET", "healthy.ts")
            assertTrue(response.startsWith("HTTP/1.1 200"))
            assertTrue(response.endsWith("healthy media"))
        }

    @Test fun `failure after response commitment does not append a second HTTP status`() =
        Fixture(initialBytes = 16_384).use { f ->
            val response = f.request("GET")
            assertTrue(response.startsWith("HTTP/1.1 200"))
            assertFalse(response.contains("HTTP/1.1 502"))
            assertTrue(f.body.closed.get())
        }

    private class Fixture(
        private val wrapper: Boolean = false,
        initialBytes: Int,
        remux: Boolean = false,
        code: Int = 200,
        failure: IOException = SocketTimeoutException("synthetic stalled origin"),
    ) : AutoCloseable {
        val body = FailingBody(initialBytes, failure)
        private val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val isWrapper = request.url.encodedPath.endsWith(".m3u8")
            val isHealthy = request.url.encodedPath.endsWith("healthy.ts")
            val responseBody = when {
                isWrapper -> "#EXTM3U\n#EXTINF:-1,Channel\nlive.ts\n".toResponseBody()
                isHealthy -> "healthy media".toResponseBody()
                else -> body
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (isWrapper || isHealthy) 200 else code).message("Origin")
                .header("Content-Type", if (isWrapper) "application/vnd.apple.mpegurl" else "video/mp2t")
                .body(responseBody).build()
        }.build()
        private val server = ProxyServer(http)

        init {
            server.start("probe-session", "127.0.0.1", remuxEnabled = remux)
        }

        fun request(method: String, path: String = if (wrapper) "wrapper.m3u8" else "live.ts"): String {
            val id = server.registerPlaylist("https://synthetic.test/$path")
            val uri = URI(server.buildLocalUrl(id))
            return Socket(uri.host, uri.port).use { socket ->
                socket.soTimeout = 3_000
                val request = "$method ${uri.rawPath} HTTP/1.1\r\nHost: localhost\r\n\r\n"
                socket.getOutputStream().write(request.toByteArray())
                socket.getInputStream().bufferedReader().readText()
            }
        }

        override fun close() {
            server.stop()
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdownNow()
        }
    }

    /** The first bytes are ready; any further read encounters a reset/timeout, never EOF. */
    private class FailingBody(initialBytes: Int, failure: IOException) : ResponseBody() {
        val reads = AtomicInteger()
        val closed = AtomicBoolean()
        private val buffered = object : Source {
            private val available = Buffer().write(ByteArray(initialBytes) { if (it % 188 == 0) 0x47 else 0 })
            override fun read(sink: Buffer, byteCount: Long): Long {
                reads.incrementAndGet()
                if (available.size == 0L) throw failure
                return available.read(sink, byteCount)
            }
            override fun close() { closed.set(true) }
            override fun timeout() = Timeout.NONE
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength() = -1L
        override fun source() = buffered
    }
}
