package com.uacastplayer.testsupport

import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeOriginServerLifecycleTest {
    @Test
    fun shutdownBetweenAcceptAndDispatchClosesTheSocketWithoutCrashing() {
        val shutdownReturned = CountDownLatch(1)
        lateinit var server: FakeOriginServer
        server = FakeOriginServer.startWithChannels(1, beforeDispatch = {
            // Force the exact interleaving that previously threw on the accept thread.
            server.shutdown()
            shutdownReturned.countDown()
        })
        try {
            val uri = URI(server.playlistUrl())
            Socket(uri.host, uri.port).use { socket ->
                socket.soTimeout = 5_000
                assertTrue(shutdownReturned.await(5, TimeUnit.SECONDS))
                assertEquals(-1, socket.getInputStream().read())
            }
            server.shutdown()
            assertEquals(0, server.activeSocketCount)
        } finally {
            server.shutdown()
        }
    }
}
