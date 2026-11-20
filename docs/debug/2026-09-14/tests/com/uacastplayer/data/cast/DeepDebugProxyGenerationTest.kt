package com.uacastplayer.data.cast

import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepDebugProxyGenerationTest {
    @Test fun `old route callback cannot install remux into a restarted proxy session`() {
        checkLateRoute(restart = true, openNext = true)
    }

    @Test fun `late previous channel response cannot replace current channel in the same session`() {
        checkLateRoute(restart = false, openNext = true)
    }

    @Test fun `stopped server without a restart rejects late remux - control`() {
        checkLateRoute(restart = true, openNext = false)
    }

    private fun checkLateRoute(restart: Boolean, openNext: Boolean) {
        val oldRouteEntered = CountDownLatch(1)
        val releaseOldRoute = CountDownLatch(1)
        val oldRouteReturned = CountDownLatch(1)
        val payload = ByteArray(188 * 800) { offset ->
            when (offset % 188) { 0 -> 0x47; 1 -> 0x1f; 2 -> 0xff; 3 -> 0x10; else -> 0 }.toByte()
        }
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").header("Content-Type", "video/mp2t")
                .body(payload.toResponseBody()).build()
        }.build()
        var oldId = ""
        val server = ProxyServer(http) { id, _ ->
            if (id == oldId) {
                oldRouteEntered.countDown()
                awaitIgnoringInterrupt(releaseOldRoute)
                oldRouteReturned.countDown()
            }
        }
        val sockets = mutableListOf<Socket>()
        val registry = ProxyServer::class.java.getDeclaredField("resourceRegistry").apply {
            isAccessible = true
        }.get(server) as ProxyResourceRegistry
        val active = ProxyResourceRegistry::class.java.getDeclaredField("activeRemuxSession").apply {
            isAccessible = true
        }
        val remuxLock = ProxyResourceRegistry::class.java.getDeclaredField("remuxLock").apply {
            isAccessible = true
        }.get(registry).let(::checkNotNull)
        fun activeId(): String? = synchronized(remuxLock) {
            (active.get(registry) as? RawTsRemuxSession)?.resourceId
        }
        try {
            server.start("old-token", "127.0.0.1")
            oldId = server.registerPlaylist("https://synthetic.test/old.ts")
            sockets += sendRequest(server.buildLocalUrl(oldId))
            assertTrue("old route never reached callback", oldRouteEntered.await(3, TimeUnit.SECONDS))

            if (restart) server.stop()
            val newId = if (openNext) {
                if (restart) server.start("new-token", "127.0.0.1")
                val id = server.registerPlaylist("https://synthetic.test/new.ts")
                sockets += sendRequest(server.buildLocalUrl(id))
                assertTrue("new remux did not start", await { server.wasRemuxed(id) })
                assertEquals(id, activeId())
                id
            } else null

            releaseOldRoute.countDown()
            assertTrue(oldRouteReturned.await(3, TimeUnit.SECONDS))
            // Wait for the old handler to finish its post-callback path (or to corrupt ownership).
            await { activeId() == oldId || server.wasRemuxed(oldId) }
            assertEquals("stale handler replaced the new session's active remux", newId, activeId())
        } finally {
            releaseOldRoute.countDown()
            server.stop()
            sockets.forEach(Socket::close)
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdownNow()
        }
    }

    private fun sendRequest(url: String): Socket {
        val uri = URI(url)
        return Socket(uri.host, uri.port).apply {
            soTimeout = 3_000
            getOutputStream().write("GET ${uri.rawPath} HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
        }
    }

    private fun await(condition: () -> Boolean): Boolean {
        repeat(300) { if (condition()) return true else Thread.sleep(10) }
        return condition()
    }

    private fun awaitIgnoringInterrupt(latch: CountDownLatch) {
        var interrupted = false
        while (latch.count != 0L) {
            try { latch.await() } catch (_: InterruptedException) { interrupted = true }
        }
        // A disk-backed callback need not react to the pool's interrupt; preserve it, as it would.
        if (interrupted) Thread.currentThread().interrupt()
    }
}
