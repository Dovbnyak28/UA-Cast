package com.uacastplayer.data.icons

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.app.IconController
import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.playlist.M3uChannel
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserIconPackPolicyTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun resetFixture() {
        application.getSharedPreferences("custom_icon_sources", Application.MODE_PRIVATE).edit().clear().commit()
        application.getSharedPreferences("uacast_icon_failures", Application.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `old automatic cache files cannot appear without an explicit pack`() = runBlocking {
        val cache = IconDiskCache(application)
        assertNotNull(cache.put("https://cdn.epg.one/logo/old-channel.png", png()))
        val repository = IconRepository(application)
        assertNull(repository.resolveIconFile("old-channel"))
        assertNull(repository.castArtworkUrl("old-channel"))
        assertTrue(repository.customIconSources().isEmpty())
    }

    @Test fun `playlist logo metadata is never fetched when no pack was added`() = runBlocking {
        Origin().use { origin ->
            val repository = IconRepository(application)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val controller = IconController(AppPreferences(application), repository,
                IconPrefetcher(application, repository), scope, {})
            try {
                val channel = M3uChannel("Channel", "https://unused.test/live", tvgId = "channel",
                    tvgLogo = "${origin.base}/provider-logo.png")
                assertNull(controller.resolveChannelIcon(channel, IconDisplayMode.CACHE))
                assertNull(controller.castArtworkUrl(channel))
                assertEquals(0, origin.requests.get())
            } finally {
                controller.dispose()
                scope.cancel()
            }
        }
    }

    @Test fun `user pack downloads the matching channel ID and survives repository recreation`() = runBlocking {
        Origin().use { origin ->
            val repository = IconRepository(application)
            repository.addCustomIconSource("${origin.base}/logos/")
            assertNotNull(repository.resolveIconFile("Новини España"))
            assertEquals("GET /logos/%D0%9D%D0%BE%D0%B2%D0%B8%D0%BD%D0%B8%20Espa%C3%B1a.png HTTP/1.1",
                origin.requestLine.get())
            val recreated = IconRepository(application)
            assertEquals(listOf("${origin.base}/logos"), recreated.customIconSources())
            assertNotNull(recreated.resolveIconFile("Новини España"))
            assertEquals(1, origin.requests.get())
        }
    }

    @Test fun `removing a pack retires positive memory and disk results`() = runBlocking {
        Origin().use { origin ->
            val repository = IconRepository(application)
            repository.addCustomIconSource(origin.base)
            assertNotNull(repository.resolveIconFile("logo"))
            repository.removeCustomIconSource(origin.base)
            assertNull(repository.resolveIconFile("logo"))
            assertNull(IconRepository(application).resolveIconFile("logo"))
            assertNull(repository.castArtworkUrl("logo"))
            assertEquals(1, origin.requests.get())
        }
    }

    @Test fun `a missing icon in the first pack falls back only to the next user pack`() = runBlocking {
        Origin(code = "404 Not Found").use { first ->
            Origin().use { second ->
                val repository = IconRepository(application)
                repository.addCustomIconSource(first.base)
                repository.addCustomIconSource(second.base)
                assertNotNull(repository.resolveIconFile("channel"))
                assertEquals(1, first.requests.get())
                assertEquals(1, second.requests.get())
                assertEquals(listOf(first.base, second.base), repository.customIconSources())
            }
        }
    }

    @Test fun `an in flight success from a removed pack cannot return a stale logo`() = runBlocking {
        Origin(hold = true).use { origin ->
            val repository = IconRepository(application)
            repository.addCustomIconSource(origin.base)
            val pending = async(Dispatchers.Default) { repository.resolveIconFile("delayed") }
            try {
                assertTrue(origin.started.await(5, TimeUnit.SECONDS))
                repository.removeCustomIconSource(origin.base)
                origin.release.countDown()
                assertNull(pending.await())
                assertNull(repository.resolveIconFile("delayed"))
                assertEquals(1, origin.requests.get())
            } finally {
                origin.release.countDown()
                pending.cancel()
            }
        }
    }

    private class Origin(private val hold: Boolean = false, private val code: String = "200 OK") : AutoCloseable {
        private val listener = ServerSocket(0)
        private val worker = Executors.newSingleThreadExecutor()
        val base = "http://127.0.0.1:${listener.localPort}"
        val requests = AtomicInteger()
        val requestLine = AtomicReference<String>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        init {
            worker.submit {
                listener.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    requestLine.set(reader.readLine())
                    while (!reader.readLine().isNullOrEmpty()) { /* headers */ }
                    requests.incrementAndGet()
                    started.countDown()
                    if (hold) check(release.await(10, TimeUnit.SECONDS))
                    val bytes = png()
                    client.getOutputStream().apply {
                        val headers = "HTTP/1.1 $code\r\nContent-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        write(headers.toByteArray())
                        write(bytes)
                        flush()
                    }
                }
            }
        }

        override fun close() {
            release.countDown()
            listener.close()
            worker.shutdownNow()
        }
    }

    private companion object {
        fun png() = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64)
    }
}
