package com.uacastplayer.data.cast

import com.uacastplayer.core.cast.TsSourceKind
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class TsFirstSegmentDiagnosticTest {

    @Test
    fun `finds the first non-tag line in a media playlist`() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            segment0.ts
            #EXTINF:6.0,
            segment1.ts
        """.trimIndent()
        assertEquals("segment0.ts", TsFirstSegmentDiagnostic.firstMediaSegmentLine(playlist))
    }

    @Test
    fun `skips blank lines between tags`() {
        val playlist = "#EXTM3U\n\n#EXT-X-VERSION:3\n\nsegment0.ts\n"
        assertEquals("segment0.ts", TsFirstSegmentDiagnostic.firstMediaSegmentLine(playlist))
    }

    @Test
    fun `trims surrounding whitespace from the segment reference`() {
        val playlist = "#EXTM3U\n  segment0.ts  \n"
        assertEquals("segment0.ts", TsFirstSegmentDiagnostic.firstMediaSegmentLine(playlist))
    }

    @Test
    fun `a playlist with only tag lines and no references returns null`() {
        val playlist = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-ENDLIST\n"
        assertNull(TsFirstSegmentDiagnostic.firstMediaSegmentLine(playlist))
    }

    @Test
    fun `an empty playlist returns null`() {
        assertNull(TsFirstSegmentDiagnostic.firstMediaSegmentLine(""))
    }

    @Test
    fun `invalid stream URL degrades to unknown instead of failing cast setup`() = runBlocking {
        val result = TsFirstSegmentDiagnostic.diagnose("not a valid stream url", testClient())

        assertNull(result.programInfo)
        assertEquals(TsSourceKind.Unknown, result.sourceKind)
    }

    @Test
    fun `cancelling an initial probe closes its origin connection`() = runBlocking {
        HangingProbeOrigin(servePlaylistBeforeHang = false).use { origin ->
            val diagnostic = async(Dispatchers.Default) {
                TsFirstSegmentDiagnostic.diagnose(origin.url, testClient())
            }
            assertTrue(
                "initial request was never accepted",
                origin.hangingRequestAccepted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )

            diagnostic.cancelAndJoin()

            assertTrue(diagnostic.isCancelled)
            assertTrue(
                "cancelled probe left its origin socket open",
                origin.clientDisconnected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
        }
    }

    @Test
    fun `cancelling an HLS segment probe closes its origin connection`() = runBlocking {
        HangingProbeOrigin(servePlaylistBeforeHang = true).use { origin ->
            val diagnostic = async(Dispatchers.Default) {
                TsFirstSegmentDiagnostic.diagnose(origin.url, testClient())
            }
            assertTrue(
                "segment request was never accepted",
                origin.hangingRequestAccepted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )

            diagnostic.cancelAndJoin()

            assertTrue(diagnostic.isCancelled)
            assertTrue(
                "cancelled segment probe left its origin socket open",
                origin.clientDisconnected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
        }
    }

    private fun testClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private class HangingProbeOrigin(private val servePlaylistBeforeHang: Boolean) : Closeable {
        val hangingRequestAccepted = CountDownLatch(1)
        val clientDisconnected = CountDownLatch(1)

        private val server = ServerSocket(0, 2, InetAddress.getLoopbackAddress())
        private val hangingClient = AtomicReference<Socket?>()
        private val worker = thread(name = "ts-diagnostic-test-origin", isDaemon = true) {
            try {
                if (servePlaylistBeforeHang) servePlaylist()
                acceptHangingRequest()
            } catch (_: IOException) {
                // Expected when close() tears down a test that failed before cancellation.
            }
        }

        val url: String = "http://${server.inetAddress.hostAddress}:${server.localPort}/playlist.m3u8"

        private fun servePlaylist() {
            server.accept().use { socket ->
                readRequestHeaders(socket)
                val body = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nsegment0.ts\n"
                    .toByteArray(StandardCharsets.UTF_8)
                val headers = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: application/vnd.apple.mpegurl\r\n")
                    append("Content-Length: ${body.size}\r\n")
                    append("Connection: close\r\n\r\n")
                }.toByteArray(StandardCharsets.US_ASCII)
                socket.getOutputStream().apply {
                    write(headers)
                    write(body)
                    flush()
                }
            }
        }

        private fun acceptHangingRequest() {
            server.accept().use { socket ->
                hangingClient.set(socket)
                socket.soTimeout = TimeUnit.SECONDS.toMillis(10).toInt()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                readRequestHeaders(reader)
                // Deliver headers but deliberately never deliver a body. This catches the harder
                // cancellation case: the HTTP call has produced a Response already and the codec
                // reader itself is blocked on the live stream.
                socket.getOutputStream().apply {
                    write(
                        "HTTP/1.1 200 OK\r\n".toByteArray(StandardCharsets.US_ASCII),
                    )
                    write(
                        "Content-Type: video/mp2t\r\n".toByteArray(StandardCharsets.US_ASCII),
                    )
                    write(
                        "Transfer-Encoding: chunked\r\n\r\n".toByteArray(StandardCharsets.US_ASCII),
                    )
                    flush()
                }
                hangingRequestAccepted.countDown()
                try {
                    while (reader.read() != -1) {
                        // A GET request has no body; any read only waits for cancellation/EOF.
                    }
                } catch (_: IOException) {
                    // Call.cancel() closes the socket and normally wakes this read with IOException.
                } finally {
                    clientDisconnected.countDown()
                    hangingClient.compareAndSet(socket, null)
                }
            }
        }

        override fun close() {
            hangingClient.getAndSet(null)?.close()
            server.close()
            worker.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))
        }

        private fun readRequestHeaders(socket: Socket) {
            readRequestHeaders(BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)))
        }

        private fun readRequestHeaders(reader: BufferedReader) {
            while (true) {
                val line = reader.readLine() ?: return
                if (line.isEmpty()) return
            }
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 3L
    }
}
