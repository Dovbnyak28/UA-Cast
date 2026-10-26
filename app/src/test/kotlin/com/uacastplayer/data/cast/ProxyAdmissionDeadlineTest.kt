package com.uacastplayer.data.cast

import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyAdmissionDeadlineTest {
    @Test fun `drip feed cannot hold all admission workers past total deadline`() {
        val served = AtomicInteger()
        val server = ProxyHttpServer(admissionTimeoutMillis = 350) { _, output ->
            served.incrementAndGet()
            output.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
            output.flush()
        }
        val port = server.start()
        val clients = List(4) { Socket("127.0.0.1", port) }
        val writer = Executors.newSingleThreadScheduledExecutor()
        try {
            clients.forEach { it.getOutputStream().write("GET /".toByteArray()) }
            writer.scheduleAtFixedRate({
                clients.forEach { runCatching { it.getOutputStream().write('a'.code) } }
            }, 0, 25, TimeUnit.MILLISECONDS)
            // Blocking read ends on the server deadline even though bytes keep arriving.
            clients.forEach { client ->
                client.soTimeout = 3_000
                val closed = runCatching { client.getInputStream().read() }
                assertTrue("deadline did not close socket: $closed", closed.exceptionOrNull() is SocketException ||
                    closed.getOrNull() == -1)
            }
            Socket("127.0.0.1", port).use { healthy ->
                healthy.soTimeout = 3_000
                healthy.getOutputStream().write("GET /healthy HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                assertTrue(healthy.getInputStream().readBytes().toString(Charsets.UTF_8).startsWith("HTTP/1.1 200"))
            }
            assertEquals(1, served.get())
        } finally {
            writer.shutdownNow()
            clients.forEach(Socket::close)
            server.stop()
        }
        assertEquals(0, server.activeClientCountForTesting())
    }
}
