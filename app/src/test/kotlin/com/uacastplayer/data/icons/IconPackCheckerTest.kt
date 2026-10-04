package com.uacastplayer.data.icons

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.playlist.M3uChannel
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IconPackCheckerTest {
    @Test fun missingHtmlOversizedAndUnavailableAreDistinctWithoutCacheWrites() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val count = AtomicInteger()
        val pool = Executors.newFixedThreadPool(3)
        ServerSocket(0).use { server ->
            pool.submit {
                repeat(4) {
                    val socket = server.accept()
                    pool.submit {
                        socket.use {
                            val input = socket.getInputStream().bufferedReader()
                            val path = input.readLine().split(' ')[1]
                            while (!input.readLine().isNullOrEmpty()) { /* Drain request headers. */ }
                            count.incrementAndGet()
                            val status = when (path) { "/missing.png" -> 404; "/down.png" -> 503; else -> 200 }
                            val body = if (path == "/large.png") "x".repeat(256 * 1024 + 1) else "<html>login</html>"
                            val header = "HTTP/1.1 $status Result\r\nContent-Length: ${body.length}\r\n" +
                                "Connection: close\r\n\r\n"
                            socket.getOutputStream().write((header + body).toByteArray())
                        }
                    }
                }
            }
            try {
                val channels = listOf("missing", "html", "large", "down").map {
                    M3uChannel(it, "https://unused.test/$it", tvgId = it)
                }
                val result = IconPackChecker(context).check("http://127.0.0.1:${server.localPort}/", channels)
                assertEquals(listOf(
                    IconPackSampleStatus.MISSING, IconPackSampleStatus.INVALID_IMAGE,
                    IconPackSampleStatus.TOO_LARGE, IconPackSampleStatus.NETWORK,
                ), result.samples.map { it.status })
                assertEquals(4, count.get())
            } finally { pool.shutdownNow() }
        }
    }

    @Test fun noIdsMeansNoNetworkRequests() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val result = IconPackChecker(context).check(
            "https://unused.example.test", listOf(M3uChannel("No ID", "https://test")),
        )
        assertEquals(emptyList<IconPackSample>(), result.samples)
        assertEquals(1, result.plan.missingIds)
    }
}
