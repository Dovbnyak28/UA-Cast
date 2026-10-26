package com.uacastplayer.data.cast

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyWriteTimeoutTest {
    @Test fun `non reading receiver releases its worker and socket without server stop`() = Fixture().use { f ->
        f.stall()
        assertTrue(f.entered.await(2, TimeUnit.SECONDS))
        assertTrue("blocked socket write did not time out", f.finished.await(3, TimeUnit.SECONDS))
        assertTrue(awaitNoClients(f.server))
        assertEquals(1, f.writeFailures.get())
        assertEquals(1, f.writeTimeouts.get())
    }

    @Test fun `stalled IP quota is reclaimed and a fresh request can be served`() = Fixture(clients = 8).use { f ->
        repeat(8) { f.stall() }
        assertTrue(f.entered.await(3, TimeUnit.SECONDS))
        assertTrue("stalled receivers kept the per-IP quota occupied", f.finished.await(4, TimeUnit.SECONDS))
        assertTrue(awaitNoClients(f.server))
        assertEquals(8, f.writeFailures.get())
        assertEquals(8, f.writeTimeouts.get())
        assertTrue(f.fetch("health").endsWith("OK"))
    }

    @Test fun `live response can outlast write timeout when individual writes keep progressing`() =
        Fixture(timeoutMillis = 250).use { f ->
            val response = f.fetch("progress")
            assertTrue(response.startsWith("HTTP/1.1 200"))
            assertEquals(6_144, response.substringAfter("\r\n\r\n").length)
            assertEquals(0, f.writeFailures.get())
        }

    @Test fun `server stop releases a writer before its long timeout`() = Fixture(timeoutMillis = 30_000).use { f ->
        f.stall()
        assertTrue(f.entered.await(2, TimeUnit.SECONDS))
        f.server.stop()
        assertTrue("stop waited for write timeout", f.finished.await(2, TimeUnit.SECONDS))
        assertTrue(awaitNoClients(f.server))
    }

    private class Fixture(clients: Int = 1, timeoutMillis: Int = 750) : AutoCloseable {
        val entered = CountDownLatch(clients)
        val finished = CountDownLatch(clients)
        val writeFailures = AtomicInteger()
        val writeTimeouts = AtomicInteger()
        private val sockets = mutableListOf<Socket>()
        val server: ProxyHttpServer = ProxyHttpServer(writeTimeoutMillis = timeoutMillis, onRequest = ::serve)
        private val port = server.start()

        private fun serve(request: ParsedRequest, output: java.io.OutputStream) {
            when (request.path) {
                "/health" -> {
                    server.writeHeaders(output, 200, "OK", mapOf("Content-Length" to "2"))
                    output.write("OK".toByteArray())
                    output.flush()
                }
                "/progress" -> {
                    server.writeHeaders(output, 200, "OK", mapOf("Content-Length" to "6144"))
                    repeat(6) {
                        output.write(ByteArray(1_024) { 'x'.code.toByte() })
                        output.flush()
                        Thread.sleep(100) // Overall response exceeds the per-operation write budget.
                    }
                }
                else -> writeUntilDisconnected(output)
            }
        }

        private fun writeUntilDisconnected(output: java.io.OutputStream) {
            try {
                server.writeHeaders(output, 200, "OK", mapOf("Content-Type" to "video/mp2t"))
                entered.countDown()
                val bytes = ByteArray(64 * 1_024)
                while (true) output.write(bytes)
            } catch (error: IOException) {
                writeFailures.incrementAndGet()
                if (error is SocketTimeoutException) writeTimeouts.incrementAndGet()
            } finally {
                finished.countDown()
            }
        }

        fun stall() { connect("stall", receiveBuffer = 1_024) }

        fun fetch(path: String): String = connect(path).getInputStream().bufferedReader().readText()

        private fun connect(path: String, receiveBuffer: Int = 64 * 1_024): Socket = Socket().also { socket ->
            sockets += socket
            socket.receiveBufferSize = receiveBuffer
            socket.soTimeout = 3_000
            socket.connect(InetSocketAddress("127.0.0.1", port))
            socket.getOutputStream().write("GET /$path HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
        }

        override fun close() {
            server.stop()
            sockets.forEach(Socket::close)
        }
    }

    private fun awaitNoClients(server: ProxyHttpServer): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (server.activeClientCountForTesting() != 0 && System.nanoTime() < deadline) Thread.sleep(5)
        return server.activeClientCountForTesting() == 0
    }
}
