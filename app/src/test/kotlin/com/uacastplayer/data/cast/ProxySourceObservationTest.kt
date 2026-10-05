package com.uacastplayer.data.cast

import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.TsSourceKind
import com.uacastplayer.core.cast.VideoCodec
import java.net.Socket
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySourceObservationTest {
    @Test fun `raw codec observation reuses the served response without another origin GET`() = Fixture().use { f ->
        assertTrue(f.fetch().startsWith("HTTP/1.1 200"))
        assertEquals(1, f.requests.get())
        val observed = f.observations.single()
        assertEquals(TsSourceKind.RawTs, observed.diagnostic.sourceKind)
        assertEquals(CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Mpeg2Video), observed.diagnostic.verdict)
        assertTrue(f.server.isSourceObservationCurrent(observed))
    }

    @Test fun `A B A rejects the first A observation even though the resource ID matches`() = Fixture().use { f ->
        f.fetch()
        val old = f.observations.single()
        f.server.registerPlaylist("https://origin.example/two", null, null)
        assertEquals(f.resourceId, f.server.registerPlaylist(Fixture.URL, null, null))
        assertFalse(f.server.isSourceObservationCurrent(old))
    }

    @Test fun `same root preserves observation ownership`() = Fixture().use { f ->
        f.fetch()
        f.server.registerPlaylist(Fixture.URL, null, null)
        assertTrue(f.server.isSourceObservationCurrent(f.observations.single()))
    }

    @Test fun `stop restart with the same token rejects queued observations`() = Fixture().use { f ->
        f.fetch()
        val old = f.observations.single()
        f.server.stop()
        f.start()
        f.server.registerPlaylist(Fixture.URL, null, null)
        assertFalse(f.server.isSourceObservationCurrent(old))
    }

    @Test fun `changed headers reject observations of the old response`() = Fixture().use { f ->
        f.fetch()
        f.server.registerPlaylist(Fixture.URL, "OtherPlayer", null)
        assertFalse(f.server.isSourceObservationCurrent(f.observations.single()))
    }

    @Test fun `HTTP error bodies never produce a codec observation`() = Fixture(code = 403).use { f ->
        assertTrue(f.fetch().startsWith("HTTP/1.1 403"))
        assertEquals(1, f.requests.get())
        assertTrue(f.observations.isEmpty())
    }

    @Test fun `disabled raw probe does not create a codec observation`() = Fixture(remux = false).use { f ->
        assertTrue(f.fetch().startsWith("HTTP/1.1 200"))
        assertTrue(f.observations.isEmpty())
    }

    @Test fun `an unwrapped source updates HLS metadata using its existing inner fetch`() =
        Fixture(wrapper = true).use { f ->
            assertTrue(f.fetch().startsWith("HTTP/1.1 200"))
            assertEquals(2, f.requests.get())
            assertEquals(listOf(TsSourceKind.Hls, TsSourceKind.RawTs), f.observations.map { it.diagnostic.sourceKind })
            assertTrue(f.observations.all { it.resourceId == f.resourceId })
        }

    @Test fun `an observer failure cannot interrupt response serving`() = Fixture(failObserver = true).use { f ->
        assertTrue(f.fetch().startsWith("HTTP/1.1 200"))
        assertEquals(1, f.requests.get())
    }

    private class Fixture(
        private val code: Int = 200,
        private val remux: Boolean = true,
        private val wrapper: Boolean = false,
        failObserver: Boolean = false,
    ) : AutoCloseable {
        val requests = AtomicInteger()
        val observations = CopyOnWriteArrayList<ProxySourceObservation>()
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            val isWrapper = wrapper && chain.request().url.encodedPath == "/live"
            val bytes = if (isWrapper) "#EXTM3U\nhttps://origin.example/inner.ts\n".toByteArray() else mpeg2Program()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("Test")
                .header("Content-Type", if (isWrapper) "application/x-mpegurl" else "video/mp2t")
                .body(bytes.toResponseBody()).build()
        }.build()
        val server = ProxyServer(client, onSourceObserved = { observed ->
            if (failObserver) error("Broken observer")
            observations.add(observed)
        })
        val resourceId: String

        init {
            start()
            resourceId = server.registerPlaylist(URL, null, null)
        }

        fun start() = server.ensureStarted("observations", "127.0.0.1", remuxEnabled = remux)

        fun fetch(): String {
            val uri = URI(server.buildLocalUrl(resourceId))
            return Socket(uri.host, uri.port).use { socket ->
                socket.soTimeout = 3_000
                socket.getOutputStream().write(
                    "GET ${uri.rawPath} HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray(),
                )
                socket.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
            }
        }

        override fun close() = server.stop()

        companion object {
            const val URL = "https://origin.example/live"

            private fun mpeg2Program(): ByteArray {
                val pat = byteArrayOf(
                    0x00, 0xB0.toByte(), 0x0D, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
                    0x00, 0x01, 0xE1.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00,
                )
                val pmt = byteArrayOf(
                    0x02, 0xB0.toByte(), 0x12, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
                    0xE1.toByte(), 0x01, 0xF0.toByte(), 0x00, 0x02, 0xE1.toByte(), 0x01, 0xF0.toByte(), 0x00,
                    0x00, 0x00, 0x00, 0x00,
                )
                return packet(0, pat) + packet(0x100, pmt) + ByteArray(188) { 0xFF.toByte() }.apply { this[0] = 0x47 }
            }

            private fun packet(pid: Int, section: ByteArray) = ByteArray(188) { 0xFF.toByte() }.apply {
                this[0] = 0x47
                this[1] = (0x40 or (pid shr 8)).toByte()
                this[2] = pid.toByte()
                this[3] = 0x10
                this[4] = 0
                section.copyInto(this, 5)
            }
        }
    }
}
