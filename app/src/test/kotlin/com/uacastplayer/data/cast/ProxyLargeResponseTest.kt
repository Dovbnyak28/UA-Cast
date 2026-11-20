package com.uacastplayer.data.cast

import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercise responses larger than a TCP window, with readers deliberately delayed at a barrier. */
class ProxyLargeResponseTest {
    @Test fun `large responses drain completely to concurrent slow receivers`() {
        val payload = ByteArray(256 * 1024) { (it % 251).toByte() }
        val started = CountDownLatch(3)
        val finished = AtomicInteger()
        val server = ProxyHttpServer { _, output ->
            started.countDown()
            val headers = "HTTP/1.1 200 OK\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n"
            output.write(headers.toByteArray())
            output.write(payload)
            output.flush()
            finished.incrementAndGet()
        }
        val clients = Executors.newFixedThreadPool(3)
        val port = server.start()
        try {
            val results = (1..3).map {
                clients.submit<Unit> {
                    Socket().use { socket ->
                        socket.receiveBufferSize = 8 * 1024
                        socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 5_000)
                        // The proxy gives each socket write up to 15 seconds before aborting a
                        // stalled receiver. A 5-second client read timeout made this test fail
                        // under a busy Gradle host while the last TCP window was still in flight;
                        // it was shorter than the server's own valid delivery budget.
                        socket.soTimeout = 20_000
                        socket.getOutputStream().write("GET /large HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                        assertTrue(started.await(5, TimeUnit.SECONDS))
                        readResponse(socket, payload, server, finished)
                    }
                }
            }
            results.forEach { it.get(30, TimeUnit.SECONDS) }
            assertEquals(3, finished.get())
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (server.activeClientCountForTesting() != 0 && System.nanoTime() < deadline) Thread.yield()
            assertEquals(0, server.activeClientCountForTesting())
        } finally {
            server.stop()
            clients.shutdownNow()
        }
    }

    private fun readResponse(socket: Socket, payload: ByteArray, server: ProxyHttpServer, finished: AtomicInteger) {
        val input = socket.getInputStream().buffered()
        val header = StringBuilder()
        while (!header.endsWith("\r\n\r\n")) {
            val value = input.read()
            assertTrue("truncated headers", value >= 0)
            header.append(value.toChar())
        }
        val received = ByteArray(payload.size)
        var offset = 0
        try {
            while (offset < received.size) {
                val count = input.read(received, offset, received.size - offset)
                check(count > 0) { "EOF at $offset" }
                offset += count
            }
        } catch (failure: java.io.IOException) {
            throw AssertionError("received=$offset/${payload.size}, finished=${finished.get()}, " +
                "active=${server.activeClientCountForTesting()}", failure)
        }
        assertArrayEquals(payload, received)
        assertEquals("server must close a completed response", -1, input.read())
    }
}
