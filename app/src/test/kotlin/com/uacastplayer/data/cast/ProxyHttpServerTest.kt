package com.uacastplayer.data.cast

import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyHttpServerTest {

    @Test
    fun `stop closes an accepted client that is blocked before its request`() {
        val server = ProxyHttpServer { _, _ -> }
        val port = server.start()
        val client = Socket("127.0.0.1", port)
        try {
            assertTrue("client was not accepted", await { server.activeClientCountForTesting() == 1 })

            server.stop()

            assertTrue("accepted socket remained tracked", await { server.activeClientCountForTesting() == 0 })
            client.soTimeout = 1_000
            val read = runCatching { client.getInputStream().read() }
            assertTrue(
                "server-side close must reach the peer immediately",
                read.getOrNull() == -1 || read.exceptionOrNull() is SocketException,
            )
        } finally {
            client.close()
            server.stop()
        }
        assertEquals(0, server.activeClientCountForTesting())
    }

    @Test
    fun `unauthorized request is rejected before response handler`() {
        val handlerCalled = AtomicBoolean(false)
        val server = ProxyHttpServer(
            onRequest = { _, _ -> handlerCalled.set(true) },
            isRequestAuthorized = { request -> request.path.startsWith("/allowed/") },
        )
        val port = server.start()
        try {
            val response = request(port, "GET /wrong/token HTTP/1.1\r\nHost: localhost\r\n\r\n")

            assertTrue(response.startsWith("HTTP/1.1 404"))
            assertFalse(handlerCalled.get())
            assertEquals(1L, server.metricsSnapshot().unauthorizedRequests)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `oversized request line is rejected by admission parser`() {
        val server = ProxyHttpServer { _, _ -> }
        val port = server.start()
        try {
            val response = request(port, "GET /${"x".repeat(5_000)} HTTP/1.1\r\n\r\n")

            assertTrue(response.startsWith("HTTP/1.1 400"))
            assertEquals(1L, server.metricsSnapshot().malformedRequests)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `missing or invalid HTTP version is rejected before response handler`() {
        val handlerCalled = AtomicBoolean(false)
        val server = ProxyHttpServer { _, _ -> handlerCalled.set(true) }
        val port = server.start()
        try {
            val malformed = listOf(
                "GET /resource\r\n\r\n",
                "GET /resource NOT-HTTP\r\n\r\n",
            )

            malformed.forEach { raw ->
                assertTrue(request(port, raw).startsWith("HTTP/1.1 400"))
            }
            assertFalse(handlerCalled.get())
            assertEquals(malformed.size.toLong(), server.metricsSnapshot().malformedRequests)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `malformed headers are rejected before authorization and response handling`() {
        val handlerCalled = AtomicBoolean(false)
        val server = ProxyHttpServer { _, _ -> handlerCalled.set(true) }
        val port = server.start()
        try {
            val malformed = listOf(
                "Broken-Header",
                "Bad Header: value",
                "X-Test: before\u0000after",
            )

            malformed.forEach { header ->
                val raw = "GET /resource HTTP/1.1\r\n$header\r\n\r\n"
                assertTrue(request(port, raw).startsWith("HTTP/1.1 400"))
            }
            assertFalse(handlerCalled.get())
            assertEquals(malformed.size.toLong(), server.metricsSnapshot().malformedRequests)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `more than 64 headers is rejected`() {
        val server = ProxyHttpServer { _, _ -> }
        val port = server.start()
        try {
            val raw = buildString {
                append("GET /resource HTTP/1.1\r\n")
                repeat(65) { append("X-Test-$it: value\r\n") }
                append("\r\n")
            }
            val response = request(port, raw)

            assertTrue(response.startsWith("HTTP/1.1 400"))
            assertEquals(1L, server.metricsSnapshot().malformedRequests)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `ninth concurrent connection from one IP is rejected`() {
        val server = ProxyHttpServer { _, _ -> }
        val port = server.start()
        val clients = List(9) { Socket("127.0.0.1", port) }
        try {
            assertTrue("per-IP rejection was not observed", await {
                server.metricsSnapshot().rejectedPerIp == 1L
            })
            assertTrue(server.activeClientCountForTesting() <= 8)
        } finally {
            clients.forEach(Socket::close)
            server.stop()
        }
    }

    @Test
    fun `stop tolerates clients closing concurrently`() {
        repeat(100) {
            val server = ProxyHttpServer { _, _ -> }
            val port = server.start()
            val client = Socket("127.0.0.1", port)
            assertTrue("client was not accepted", await { server.activeClientCountForTesting() == 1 })

            val peerCloser = Thread { client.close() }.apply { start() }
            server.stop()
            peerCloser.join()

            assertEquals(0, server.activeClientCountForTesting())
        }
    }

    @Test
    fun `concurrent lifecycle calls leave one usable server generation`() {
        lateinit var server: ProxyHttpServer
        server = ProxyHttpServer { _, output -> server.writeError(output, 404, "Not Found") }
        val failures = ConcurrentLinkedQueue<Throwable>()
        val startGate = CountDownLatch(1)
        val workers = List(2) {
            Thread {
                startGate.await()
                repeat(25) {
                    runCatching {
                        server.start()
                        server.stop()
                    }.exceptionOrNull()?.let(failures::add)
                }
            }.apply { start() }
        }

        startGate.countDown()
        workers.forEach(Thread::join)

        try {
            assertTrue("concurrent start/stop threw $failures", failures.isEmpty())
            val port = server.start()
            val response = request(port, "GET /health HTTP/1.1\r\nHost: localhost\r\n\r\n")
            assertTrue("final proxy generation is not reachable", response.startsWith("HTTP/1.1 404"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `handler from stopped generation cannot release new generation IP slot`() {
        val oldHandlerEntered = CountDownLatch(1)
        val releaseOldHandler = CountDownLatch(1)
        val oldHandlerExited = CountDownLatch(1)
        val server = ProxyHttpServer { request, _ ->
            if (request.path == "/old") {
                oldHandlerEntered.countDown()
                try {
                    awaitIgnoringInterrupt(releaseOldHandler)
                } finally {
                    oldHandlerExited.countDown()
                }
            }
        }
        val oldPort = server.start()
        val oldClient = Socket("127.0.0.1", oldPort)
        val newClients = mutableListOf<Socket>()
        try {
            oldClient.getOutputStream().apply {
                write("GET /old HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                flush()
            }
            assertTrue("old handler did not start", oldHandlerEntered.await(2, TimeUnit.SECONDS))

            server.stop()
            val newPort = server.start()
            repeat(8) { newClients += Socket("127.0.0.1", newPort) }
            assertTrue("new generation did not acquire eight slots", await {
                server.activeClientCountForTesting() == 8
            })

            releaseOldHandler.countDown()
            assertTrue("old handler did not finish", oldHandlerExited.await(2, TimeUnit.SECONDS))
            newClients += Socket("127.0.0.1", newPort)

            assertTrue("ninth new-generation client bypassed the per-IP limit", await {
                server.metricsSnapshot().rejectedPerIp == 1L
            })
            assertTrue(server.activeClientCountForTesting() <= 8)
        } finally {
            releaseOldHandler.countDown()
            oldClient.close()
            newClients.forEach(Socket::close)
            server.stop()
        }
    }

    @Test
    fun `late unauthorized result from stopped generation cannot contaminate new metrics`() {
        val oldAuthorizationEntered = CountDownLatch(1)
        val releaseOldAuthorization = CountDownLatch(1)
        val oldRequestFinished = CountDownLatch(1)
        lateinit var server: ProxyHttpServer
        server = ProxyHttpServer(
            isRequestAuthorized = { request ->
                if (request.path == "/old") {
                    oldAuthorizationEntered.countDown()
                    awaitIgnoringInterrupt(releaseOldAuthorization)
                    false
                } else {
                    true
                }
            },
            onRequest = { _, output -> server.writeError(output, 404, "Not Found") },
        )
        val oldPort = server.start()
        val oldRequest = Thread {
            runCatching { request(oldPort, "GET /old HTTP/1.1\r\nHost: localhost\r\n\r\n") }
            oldRequestFinished.countDown()
        }.apply { start() }

        try {
            assertTrue("old authorization did not start", oldAuthorizationEntered.await(2, TimeUnit.SECONDS))
            server.stop()

            val newPort = server.start()
            assertEquals(ProxyHttpMetricsSnapshot(0, 0, 0, 0, 0, 0), server.metricsSnapshot())
            assertTrue(request(newPort, "GET /new HTTP/1.1\r\nHost: localhost\r\n\r\n").startsWith("HTTP/1.1 404"))

            releaseOldAuthorization.countDown()
            assertTrue("old request did not finish", oldRequestFinished.await(2, TimeUnit.SECONDS))

            val current = server.metricsSnapshot()
            assertEquals(1L, current.acceptedConnections)
            assertEquals(0L, current.unauthorizedRequests)
        } finally {
            releaseOldAuthorization.countDown()
            oldRequest.join(2_000)
            server.stop()
        }
    }

    private fun request(port: Int, raw: String): String = Socket("127.0.0.1", port).use { client ->
        client.soTimeout = 2_000
        client.getOutputStream().apply {
            write(raw.toByteArray(Charsets.ISO_8859_1))
            flush()
        }
        client.getInputStream().bufferedReader(Charsets.ISO_8859_1).readText()
    }

    private fun await(condition: () -> Boolean): Boolean {
        repeat(100) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    private fun awaitIgnoringInterrupt(latch: CountDownLatch) {
        while (latch.count > 0) {
            try {
                latch.await()
            } catch (_: InterruptedException) {
                // ProxyHttpServer.stop() interrupts the old response pool. The test deliberately
                // keeps this handler alive to reproduce its late release after the next start().
            }
        }
    }
}
