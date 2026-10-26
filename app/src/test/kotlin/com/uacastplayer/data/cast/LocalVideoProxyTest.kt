package com.uacastplayer.data.cast

import android.os.CancellationSignal
import com.uacastplayer.data.media.LocalVideoHandle
import com.uacastplayer.data.media.LocalVideoSource
import com.uacastplayer.media.LocalVideo
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalVideoProxyTest {
    private val source = SyntheticVideoSource()
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    private lateinit var server: ProxyServer
    private lateinit var url: String
    private val video = LocalVideo("content://media/video/1", "Movie.mp4", "video/mp4")

    @Before fun start() {
        val forbiddenOrigin = OkHttpClient.Builder()
            .addInterceptor { error("Local video must not use upstream HTTP") }.build()
        server = ProxyServer(forbiddenOrigin, localVideoSource = source)
        server.start("private-session", "127.0.0.1", flattenHlsToStream = true)
        url = server.buildLocalUrl(server.registerPlaylist(video.uri, localVideo = video))
    }

    @After fun stop() {
        source.release.countDown()
        server.stop()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test fun getStreamsExactBytesWithTheFilesMimeAndClosesItsDescriptor() {
        client.newCall(Request.Builder().url(url).build()).execute().use {
            assertEquals(200, it.code)
            assertEquals("video/mp4", it.header("Content-Type"))
            assertEquals("bytes", it.header("Accept-Ranges"))
            assertEquals("no-store", it.header("Cache-Control"))
            assertArrayEquals(ByteArray(1024) { index -> index.toByte() }, it.body.bytes())
        }
        assertClosed(1)
        assertEquals(1024, server.bytesServedToReceiver())
    }

    @Test fun headPublishesLengthWithoutReadingOrSeekingTheBody() {
        client.newCall(Request.Builder().url(url).head().build()).execute().use {
            assertEquals(200, it.code)
            assertEquals("1024", it.header("Content-Length"))
            assertEquals(0, it.body.bytes().size)
        }
        assertClosed(1)
        assertEquals(0, source.reads.get())
        assertEquals(0, source.seeks.get())
    }

    @Test fun rangesServeOnlyTheRequestedIntervalIncludingSuffix() {
        for (range in listOf("bytes=768-1023", "bytes=-256")) {
            client.newCall(Request.Builder().url(url).header("Range", range).build()).execute().use {
                assertEquals(206, it.code)
                assertEquals("bytes 768-1023/1024", it.header("Content-Range"))
                assertArrayEquals(ByteArray(256) { index -> index.toByte() }, it.body.bytes())
            }
        }
        assertClosed(2)
    }

    @Test fun invalidRangeReturns416WithoutReadingTheFile() {
        client.newCall(Request.Builder().url(url).header("Range", "bytes=10000-").build()).execute().use {
            assertEquals(416, it.code)
            assertEquals("bytes */1024", it.header("Content-Range"))
            assertEquals(0, it.body.bytes().size)
        }
        assertClosed(1)
        assertEquals(0, source.reads.get())
    }

    @Test fun tokenAndUnknownResourceAreRejectedBeforeOpeningAnyDocument() {
        for (invalid in listOf(url.replace("private-session", "wrong-session"), "$url/../../other")) {
            client.newCall(Request.Builder().url(invalid).build()).execute().use { assertEquals(404, it.code) }
        }
        assertEquals(0, source.opened.get())
    }

    @Test fun channelReplacementRetiresTheOldFileUrl() {
        val replacement = video.copy(uri = "content://media/video/2", mimeType = "video/webm")
        val newUrl = server.buildLocalUrl(server.registerPlaylist(replacement.uri, localVideo = replacement))
        client.newCall(Request.Builder().url(url).build()).execute().use { assertEquals(404, it.code) }
        client.newCall(Request.Builder().url(newUrl).head().build()).execute().use {
            assertEquals(200, it.code)
            assertEquals("video/webm", it.header("Content-Type"))
        }
        assertEquals(1, source.opened.get())
    }

    @Test fun largeFileSeekUsesLongOffsetsWithoutAllocatingTheWholeMovie() {
        source.length = 5L * 1024 * 1024 * 1024
        client.newCall(Request.Builder().url(url).header("Range", "bytes=4294967296-4294967551").build())
            .execute().use {
                assertEquals(206, it.code)
                assertArrayEquals(ByteArray(256) { index -> index.toByte() }, it.body.bytes())
            }
        assertClosed(1)
    }

    @Test fun stopDuringProviderOpenCancelsTheOpenAndClosesAnyLateDescriptor() {
        source.holdOpen = true
        val worker = Executors.newSingleThreadExecutor()
        try {
            val future = worker.submit {
                runCatching { client.newCall(Request.Builder().url(url).build()).execute().close() }
            }
            assertTrue(source.entered.await(3, TimeUnit.SECONDS))
            server.stop()
            assertEquals(1, source.cancelled.get())
            future.get(3, TimeUnit.SECONDS)
            assertClosed(1)
        } finally {
            source.release.countDown()
            worker.shutdownNow()
        }
    }

    @Test fun aThrowingProviderCancellationCannotPreventDescriptorClosure() {
        source.throwOnCancel = true
        client.newCall(Request.Builder().url(url).build()).execute().use {
            assertEquals(200, it.code)
            assertEquals(1024, it.body.bytes().size)
        }
        server.stop()
        assertClosed(1)
    }

    private fun assertClosed(expected: Int) {
        // Content-Length lets the client finish before the server worker reaches finally.
        // Await actual descriptor closure; receiving the bytes is not a cleanup barrier.
        assertTrue("File descriptors must close after their response",
            source.closeEvents.tryAcquire(expected, 3, TimeUnit.SECONDS))
        assertEquals(expected, source.closed.get())
    }
}

private class SyntheticVideoSource : LocalVideoSource {
    var length = 1024L
    var holdOpen = false
    var throwOnCancel = false
    val opened = AtomicInteger()
    val closed = AtomicInteger()
    val closeEvents = Semaphore(0)
    val cancelled = AtomicInteger()
    val reads = AtomicInteger()
    val seeks = AtomicInteger()
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)

    override fun open(video: LocalVideo, cancellation: CancellationSignal): LocalVideoHandle {
        opened.incrementAndGet()
        cancellation.setOnCancelListener {
            cancelled.incrementAndGet()
            release.countDown()
            if (throwOnCancel) throw IllegalStateException("Provider cancellation callback failed")
        }
        entered.countDown()
        if (holdOpen) check(release.await(3, TimeUnit.SECONDS))
        return object : LocalVideoHandle {
            private var position = 0L
            private var disposed = false
            override val length = this@SyntheticVideoSource.length
            override val input = object : InputStream() {
                override fun read(): Int {
                    reads.incrementAndGet()
                    return if (position >= length) -1 else (position++ and 255).toInt()
                }
            }
            override fun seek(position: Long) { seeks.incrementAndGet(); this.position = position }
            override fun close() {
                if (!disposed) {
                    disposed = true
                    closed.incrementAndGet()
                    closeEvents.release()
                }
            }
        }
    }
}
