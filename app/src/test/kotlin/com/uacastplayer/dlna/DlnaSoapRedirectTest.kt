package com.uacastplayer.dlna

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DlnaSoapRedirectTest {

    @Test
    fun `AVTransport does not treat a redirected POST's GET response as success`() = RedirectServer().use { server ->
        val client = AvTransportClient(OkHttpClient())

        val accepted = runBlocking {
            client.setAvTransportUri(server.url, "http://127.0.0.1/live", "News")
        }

        assertFalse(accepted)
        assertEquals(1, server.requests.get())
    }

    @Test
    fun `RenderingControl does not treat a redirected volume command as success`() = RedirectServer().use { server ->
        val client = RenderingControlClient(OkHttpClient())

        val accepted = runBlocking { client.setVolume(server.url, 25) }

        assertFalse(accepted)
        assertEquals(1, server.requests.get())
    }

    @Test
    fun `watchdog does not follow a redirected status query`() = RedirectServer().use { server ->
        val reader = DlnaTransportStateReader(OkHttpClient())

        val health = runBlocking { reader.read(server.url) }

        assertEquals(DlnaTransportHealth.UNREACHABLE, health)
        assertEquals(1, server.requests.get())
    }

    private class RedirectServer : AutoCloseable {
        private val socket = ServerSocket(0)
        val requests = AtomicInteger()
        val url = "http://127.0.0.1:${socket.localPort}/control"
        private val worker = thread(name = "dlna-redirect-test") {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: IOException) {
                    break
                }
                serve(client)
            }
        }

        private fun serve(client: Socket) {
            client.use {
                it.soTimeout = 2_000
                it.getInputStream().read(ByteArray(4096))
                val response = if (requests.incrementAndGet() == 1) {
                    "HTTP/1.1 302 Found\r\nLocation: $url/redirected\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                } else {
                    "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                }
                it.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
                it.getOutputStream().flush()
            }
        }

        override fun close() {
            socket.close()
            worker.join(2_000)
        }
    }
}
