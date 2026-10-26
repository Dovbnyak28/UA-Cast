package com.uacastplayer.data.cast

import com.uacastplayer.core.cast.TsSourceKind
import com.uacastplayer.core.cast.VideoCodec
import com.uacastplayer.core.net.HttpDefaults
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Exercises the HTTP boundary, not just the byte classifier: even a valid TS/playlist body
 * must not turn a provider's rejection into a codec verdict or another fetch. */
class TsDiagnosticHttpTest {

    @Test
    fun `a rejected initial TS response is not a stream or a codec verdict`() = runBlocking {
        val client = origin { response(it, 404, "video/mp2t", mpeg2Program()) }

        val result = TsFirstSegmentDiagnostic.diagnose(STREAM_URL, client)

        assertEquals(TsSourceKind.Unknown, result.sourceKind)
        assertNull(result.programInfo)
    }

    @Test
    fun `a rejected playlist response never triggers a segment request`() = runBlocking {
        val paths = mutableListOf<String>()
        val client = origin { request ->
            paths += request.url.encodedPath
            response(request, 403, "application/vnd.apple.mpegurl", MEDIA_PLAYLIST.toByteArray())
        }

        val result = TsFirstSegmentDiagnostic.diagnose(STREAM_URL, client)

        assertEquals(listOf("/live"), paths)
        assertEquals(TsSourceKind.Unknown, result.sourceKind)
        assertNull(result.programInfo)
    }

    @Test
    fun `a rejected segment cannot block casting based on its error body`() = runBlocking {
        val client = origin { request ->
            if (request.url.encodedPath == "/live") {
                response(request, 200, "application/vnd.apple.mpegurl", MEDIA_PLAYLIST.toByteArray())
            } else {
                response(request, 403, "video/mp2t", mpeg2Program())
            }
        }

        val result = TsFirstSegmentDiagnostic.diagnose(STREAM_URL, client)

        assertEquals(TsSourceKind.Hls, result.sourceKind)
        assertNull(result.programInfo)
    }

    @Test
    fun `successful partial TS response still supplies codec information`() = runBlocking {
        val client = origin { response(it, 206, "video/mp2t", mpeg2Program()) }

        val result = TsFirstSegmentDiagnostic.diagnose(STREAM_URL, client)

        assertEquals(TsSourceKind.RawTs, result.sourceKind)
        assertEquals(VideoCodec.Mpeg2Video, result.programInfo?.videoCodec)
    }

    @Test
    fun `master variant URL is not fetched as if it were a media segment`() = runBlocking {
        val paths = mutableListOf<String>()
        val client = origin { request ->
            paths += request.url.encodedPath
            val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000\nvariant.m3u8\n"
            response(request, 200, "application/vnd.apple.mpegurl", master.toByteArray())
        }

        val result = TsFirstSegmentDiagnostic.diagnose(STREAM_URL, client)

        assertEquals(listOf("/live"), paths)
        assertEquals(TsSourceKind.Hls, result.sourceKind)
        assertNull(result.programInfo)
    }

    @Test
    fun `segment network failure preserves the already proven HLS source kind`() = runBlocking {
        val client = origin { request ->
            if (request.url.encodedPath == "/live") {
                response(request, 200, "application/vnd.apple.mpegurl", MEDIA_PLAYLIST.toByteArray())
            } else {
                throw IOException("Synthetic segment connection reset")
            }
        }

        val result = TsFirstSegmentDiagnostic.diagnose(STREAM_URL, client)

        assertEquals(TsSourceKind.Hls, result.sourceKind)
        assertNull(result.programInfo)
    }

    @Test
    fun `both playlist and segment use the channel access headers`() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = origin { request ->
            requests += request
            if (request.header("User-Agent") != "ProviderPlayer/1.0" ||
                request.header("Referer") != "https://provider.example/player"
            ) {
                response(request, 403, "text/plain", byteArrayOf())
            } else if (request.url.encodedPath == "/live") {
                response(request, 200, "application/vnd.apple.mpegurl", MEDIA_PLAYLIST.toByteArray())
            } else {
                response(request, 206, "video/mp2t", mpeg2Program())
            }
        }

        val result = TsFirstSegmentDiagnostic.diagnose(
            STREAM_URL, client, " ProviderPlayer/1.0 ", " https://provider.example/player ",
        )

        assertEquals(listOf("/live", "/segment.ts"), requests.map { it.url.encodedPath })
        requests.forEach { assertEquals("bytes=0-262143", it.header("Range")) }
        assertEquals(TsSourceKind.Hls, result.sourceKind)
        assertEquals(VideoCodec.Mpeg2Video, result.programInfo?.videoCodec)
    }

    @Test
    fun `invalid channel headers use the same safe defaults as Media3`() = runBlocking {
        val client = origin { request ->
            assertEquals(HttpDefaults.BROWSER_USER_AGENT, request.header("User-Agent"))
            assertNull(request.header("Referer"))
            response(request, 206, "video/mp2t", mpeg2Program())
        }

        val result = TsFirstSegmentDiagnostic.diagnose(
            STREAM_URL, client, "bad\r\nInjected: value", "https://provider.example/\nInjected: value",
        )

        assertEquals(TsSourceKind.RawTs, result.sourceKind)
        assertEquals(VideoCodec.Mpeg2Video, result.programInfo?.videoCodec)
    }

    private fun origin(reply: (Request) -> Response): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain -> reply(chain.request()) }
        .build()

    private fun response(request: Request, code: Int, contentType: String, bytes: ByteArray): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Test")
            .header("Content-Type", contentType)
            .body(bytes.toResponseBody(contentType.toMediaType()))
            .build()

    // PAT + PMT with one MPEG-2 elementary stream. CRC is unused by the diagnostic parser.
    private fun mpeg2Program(): ByteArray {
        val pat = byteArrayOf(
            0x00, 0xB0.toByte(), 0x0D, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
            0x00, 0x01, 0xE1.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        val pmt = byteArrayOf(
            0x02, 0xB0.toByte(), 0x12, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
            0xE1.toByte(), 0x01, 0xF0.toByte(), 0x00,
            0x02, 0xE1.toByte(), 0x01, 0xF0.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x00,
        )
        return packet(0, pat) + packet(0x100, pmt)
    }

    private fun packet(pid: Int, section: ByteArray): ByteArray = ByteArray(188) { 0xFF.toByte() }.apply {
        this[0] = 0x47
        this[1] = (0x40 or ((pid shr 8) and 0x1F)).toByte()
        this[2] = (pid and 0xFF).toByte()
        this[3] = 0x10
        this[4] = 0x00
        section.copyInto(this, destinationOffset = 5)
    }

    private companion object {
        const val STREAM_URL = "https://origin.example/live"
        const val MEDIA_PLAYLIST = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nsegment.ts\n"
    }
}
