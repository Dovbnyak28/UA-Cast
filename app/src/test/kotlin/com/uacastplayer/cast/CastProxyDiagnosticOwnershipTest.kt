package com.uacastplayer.cast

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CastProxyDiagnosticOwnershipTest {
    @Test fun `only the current attempt may consume its source observation`() = Fixture().use { f ->
        val observation = f.observe()
        assertTrue(f.proxy.isDiagnosticCurrent(observation))
        assertEquals(1, f.originRequests.get())
    }

    @Test fun `a new attempt invalidates a queued observation even on the same root`() = Fixture().use { f ->
        val old = f.observe()
        f.proxy.beginPlaybackAttempt()
        f.prepare()
        assertFalse(f.proxy.isDiagnosticCurrent(old))
        assertTrue(f.proxy.isDiagnosticCurrent(f.observe()))
    }

    @Test fun `thirty rapid channel switches cannot revive an old source observation`() = Fixture().use { f ->
        val old = f.observe()
        repeat(30) { index ->
            f.proxy.beginPlaybackAttempt()
            f.prepare("https://origin.example/channel-$index")
        }
        f.proxy.beginPlaybackAttempt()
        f.prepare()
        assertFalse(f.proxy.isDiagnosticCurrent(old))
        assertEquals("switching channels must not initiate speculative fetches", 1, f.originRequests.get())
    }

    @Test fun `stop invalidates observations before a same-token restart`() = Fixture().use { f ->
        val old = f.observe()
        f.proxy.stop()
        assertFalse(f.proxy.isDiagnosticCurrent(old))
        f.prepare()
        assertFalse(f.proxy.isDiagnosticCurrent(old))
        assertTrue(f.proxy.isDiagnosticCurrent(f.observe()))
    }

    @Test fun `changed access headers invalidate a result without requiring a new URL`() = Fixture().use { f ->
        val old = f.observe()
        f.prepare(userAgent = "OtherPlayer")
        assertFalse(f.proxy.isDiagnosticCurrent(old))
    }

    private class Fixture : AutoCloseable {
        val originRequests = AtomicInteger()
        private val observations = CopyOnWriteArrayList<CastProxyDiagnostic>()
        private val upstream = OkHttpClient.Builder().addInterceptor { chain ->
            originRequests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").header("Content-Type", "application/vnd.apple.mpegurl")
                .body("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment.ts\n".toResponseBody()).build()
        }.build()
        val proxy = CastProxySession(
            ApplicationProvider.getApplicationContext<Application>(), upstream, observations::add,
        )
        private val receiver = OkHttpClient()
        private var localUrl: String

        init {
            proxy.adoptToken("proxy-diagnostic-ownership")
            proxy.beginPlaybackAttempt()
            localUrl = prepare().localUrl
        }

        fun prepare(url: String = "https://origin.example/live", userAgent: String? = null): PreparedCastProxy =
            proxy.prepare("127.0.0.1", url, "Live", userAgent, null, "TV").getOrThrow().also {
                localUrl = it.localUrl
            }

        fun observe(): CastProxyDiagnostic {
            receiver.newCall(Request.Builder().url(localUrl).build()).execute().use { response ->
                assertEquals(200, response.code)
                response.body.string()
            }
            return observations.last()
        }

        override fun close() = proxy.stop()
    }
}
