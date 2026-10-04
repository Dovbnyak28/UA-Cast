package com.uacastplayer.cast

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CastLocalNetworkOwnershipTest {

    @Test
    fun `remembering a locally playing channel never steals its sole origin connection`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Application>()
        // Instantiate an isolated repository: the app singleton can be used by other Robolectric
        // tests, so neither their active channel nor their session state belongs to this test.
        val constructor = CastSessionRepository::class.java.getDeclaredConstructor(
            Context::class.java, CoroutineDispatcher::class.java,
        ).apply { isAccessible = true }
        val repository = constructor.newInstance(context, Dispatchers.IO)
        val scope = CastSessionRepository::class.java.getDeclaredField("scope")
            .apply { isAccessible = true }.get(repository) as CoroutineScope
        try {
            SingleConnectionOrigin().use { origin ->
                OkHttpClient().newCall(Request.Builder().url(origin.url).build()).execute().use {
                    repository.setActiveChannel(CastChannel(0, origin.url, "Local only"))
                    advanceTimeBy(2_000)
                    runCurrent()

                    assertFalse(
                        "Cast warm-up opened a second GET and displaced local playback without a Cast session",
                        origin.displacedPlayer.await(1, TimeUnit.SECONDS),
                    )
                }
            }
        } finally {
            scope.cancel()
            Dispatchers.resetMain()
        }
    }

    /** Simulates one-slot IPTV: a second GET replaces the first, already-playing connection. */
    private class SingleConnectionOrigin : Closeable {
        private val server = ServerSocket(0, 2, InetAddress.getLoopbackAddress())
        private val playerSocket = AtomicReference<Socket?>()
        val displacedPlayer = CountDownLatch(1)
        val url = "http://${server.inetAddress.hostAddress}:${server.localPort}/live"
        private val worker = thread(name = "single-connection-cast-origin", isDaemon = true) {
            try {
                val first = server.accept()
                playerSocket.set(first)
                readHeaders(first)
                first.getOutputStream().apply {
                    val headers = "HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\n" +
                        "Transfer-Encoding: chunked\r\n\r\n"
                    write(headers.toByteArray())
                    flush()
                }
                server.accept().use { second ->
                    readHeaders(second)
                    playerSocket.getAndSet(null)?.close()
                    displacedPlayer.countDown()
                    second.getOutputStream().apply {
                        write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        flush()
                    }
                }
            } catch (_: IOException) {
                // close() interrupts accept/read when no diagnostic request was made.
            }
        }

        private fun readHeaders(socket: Socket) {
            socket.soTimeout = 3_000
            val reader = socket.getInputStream().bufferedReader()
            while (true) {
                if (reader.readLine().isNullOrEmpty()) return
            }
        }

        override fun close() {
            server.close()
            playerSocket.getAndSet(null)?.close()
            worker.join(3_000)
        }
    }
}
