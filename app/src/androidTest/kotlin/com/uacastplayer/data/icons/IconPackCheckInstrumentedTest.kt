package com.uacastplayer.data.icons

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.playlist.M3uChannel
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android image decoder, bounded loopback fixture. No provider/CDN or user playlist accessed. */
@RunWith(AndroidJUnit4::class)
class IconPackCheckInstrumentedTest {
    @Test fun decodableImageMissingIdAndCorruptPngProduceDistinctResults(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            try { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
            finally { bitmap.recycle() }
        }
        val pool = Executors.newSingleThreadExecutor()
        ServerSocket(0).use { server ->
            pool.submit {
                repeat(3) {
                    server.accept().use { socket ->
                        val input = socket.getInputStream().bufferedReader()
                        val path = input.readLine().split(' ')[1]
                        while (!input.readLine().isNullOrEmpty()) { /* Drain headers. */ }
                        val status = if (path == "/missing.png") 404 else 200
                        val bytes = if (path == "/good.png") image else image.copyOf(12)
                        val header = "HTTP/1.1 $status Result\r\nContent-Length: ${bytes.size}\r\n" +
                            "Content-Type: image/png\r\nConnection: close\r\n\r\n"
                        socket.getOutputStream().apply { write(header.toByteArray()); write(bytes) }
                    }
                }
            }
            try {
                val channels = listOf("good", "missing", "broken").map {
                    M3uChannel(it, "https://unused.test/$it", tvgId = it)
                } + M3uChannel("No ID", "https://unused.test/no-id")
                val result = IconPackChecker(context).check("http://127.0.0.1:${server.localPort}", channels)
                assertEquals(1, result.plan.missingIds)
                assertEquals(listOf(IconPackSampleStatus.FOUND, IconPackSampleStatus.MISSING,
                    IconPackSampleStatus.INVALID_IMAGE), result.samples.map { it.status })
                assertNotNull(result.samples.first().bytes)
            } finally { pool.shutdownNow() }
        }
    }
}
