package com.uacastplayer.data.icons

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Which icon fetches are remembered as having failed.
 *
 * [IconFailureStore] exists so a URL that did not work is not asked again for a while - it is
 * consulted before every fetch. It was only ever *told* about two things: a non-2xx response and an
 * IOException. The third kind was invisible to it, and it is the one that happens most.
 *
 * An origin with hotlink protection commonly refuses by answering **200 with an HTML page** rather
 * than with the 403 the status branch catches. That produced a successful response, a body that is
 * not an image, a null from the disk cache, and no record anywhere - so the same URL was fetched
 * again on the next prefetch pass, and the one after that, forever. On a 3,500-channel playlist
 * whose provider serves logos from such a host, that is a few thousand pointless requests per
 * playlist load.
 *
 * The origin here is a hand-rolled [ServerSocket] - no MockWebServer in this project, see
 * `CastRoutingIntegrationTest` - and it counts requests, because "was it asked again" is the whole
 * question.
 */
@RunWith(RobolectricTestRunner::class)
class IconFetchFailureTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    private var origin: Origin? = null

    @Before
    fun setUp() {
        // The failure store is SharedPreferences and the disk cache is filesDir; both outlive a
        // single test method under Robolectric.
        application.getSharedPreferences("uacast_icon_failures", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        java.io.File(application.filesDir, "icon_cache").deleteRecursively()
    }

    @After
    fun tearDown() {
        origin?.close()
    }

    private class Origin(private val contentType: String, private val body: ByteArray) : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newCachedThreadPool()
        val requests = AtomicInteger(0)

        fun urlFor(path: String) = "http://127.0.0.1:${socket.localPort}$path"

        init {
            worker.execute {
                while (!socket.isClosed) {
                    try {
                        val accepted = socket.accept()
                        worker.execute { serve(accepted) }
                    } catch (_: IOException) {
                        return@execute
                    }
                }
            }
        }

        private fun serve(client: Socket) {
            client.use {
                it.getInputStream().read(ByteArray(2048))
                requests.incrementAndGet()
                val out = it.getOutputStream()
                out.write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\n" +
                        "Content-Length: ${body.size}\r\n\r\n").toByteArray(),
                )
                out.write(body)
                out.flush()
            }
        }

        override fun close() {
            runCatching { socket.close() }
            worker.shutdownNow()
        }
    }

    /** A real PNG, by its magic bytes - what the format sniff is actually looking for. */
    private fun pngBytes(): ByteArray =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64)

    private fun resolveTwice(origin: Origin): Pair<java.io.File?, java.io.File?> {
        val repository = IconRepository(application)
        val url = origin.urlFor("/logo.png")
        val first = runBlocking { repository.resolveIconFile(tvgLogo = url, epgIconUrl = null, tvgId = null) }
        // The in-memory cache would answer the second call on its own, which is not what is under
        // test - the question is whether the *failure store* learned anything.
        repository.invalidateMemoryCache()
        val second = runBlocking { repository.resolveIconFile(tvgLogo = url, epgIconUrl = null, tvgId = null) }
        return first to second
    }

    /** The bug: hotlink protection answering 200 with a page instead of a picture. */
    @Test
    fun `a 200 that is not an image is not fetched again`() {
        val origin = Origin("text/html", "<html><body>Hotlinking is not allowed</body></html>".toByteArray())
            .also { this.origin = it }

        val (first, second) = resolveTwice(origin)

        assertNull(first)
        assertNull(second)
        assertEquals("the same non-image url must not be fetched twice", 1, origin.requests.get())
    }

    /** A file past the cache's own size limit: downloaded in full before being rejected, so
     * repeating it is the most expensive of these mistakes. */
    @Test
    fun `an icon past the size cap is not fetched again`() {
        val huge = pngBytes() + ByteArray(IconDiskCache.MAX_ICON_BYTES + 1)
        val origin = Origin("image/png", huge).also { this.origin = it }

        val (first, second) = resolveTwice(origin)

        assertNull(first)
        assertNull(second)
        assertEquals(1, origin.requests.get())
    }

    /**
     * The control, and what stops the two above from being satisfied by a repository that simply
     * never fetches: a real image is resolved, and the second call is served without asking the
     * origin again because it is now on disk.
     */
    @Test
    fun `a real image resolves and is then served from disk`() {
        val origin = Origin("image/png", pngBytes()).also { this.origin = it }

        val (first, second) = resolveTwice(origin)

        assertNotNull("a real PNG must resolve", first)
        assertNotNull(second)
        assertEquals("the second resolve must come from disk", 1, origin.requests.get())
    }
}
