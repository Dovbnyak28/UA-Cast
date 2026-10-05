package com.uacastplayer.data.cast

import com.uacastplayer.proxy.HlsMediaPlaylistParser
import com.uacastplayer.proxy.HlsPlaylistBudget
import com.uacastplayer.proxy.PlaylistUnwrapPolicy
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPipelineBudgetTest {
    private val hostile = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n" + "a\n".repeat(2_000_000)
    private val healthy = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts\n"

    @Test fun `unwrap and standalone parser reject former 128 MiB OOM input`() {
        assertEquals(4_000_032, hostile.length)
        assertThrows(IOException::class.java) { PlaylistUnwrapPolicy.unwrapTarget(hostile, ORIGIN) }
        assertThrows(IOException::class.java) { HlsMediaPlaylistParser.parse(hostile) }
    }

    @Test fun `CR LF and CRLF floods share the same line budget`() {
        for (newline in listOf("\r", "\n", "\r\n")) {
            assertFalse(HlsPlaylistBudget.accepts("#EXTM3U" + newline.repeat(HlsPlaylistBudget.MAX_LINES)))
        }
        assertTrue(HlsPlaylistBudget.accepts("#EXTM3U\n" + "a\n".repeat(HlsPlaylistBudget.MAX_REFERENCES)))
        assertFalse(HlsPlaylistBudget.accepts("#EXTM3U\n" + "a\n".repeat(HlsPlaylistBudget.MAX_REFERENCES + 1)))
    }

    @Test fun `actual proxy rejects before default unwrap and remains usable`() =
        checkProxy(unwrap = true, flatten = false)
    @Test fun `actual proxy rejects before flatten and remains usable`() = checkProxy(unwrap = false, flatten = true)
    @Test fun `actual proxy rejects before plain rewrite and remains usable`() =
        checkProxy(unwrap = false, flatten = false)

    private fun checkProxy(unwrap: Boolean, flatten: Boolean) {
        val body = AtomicReference(healthy)
        val upstream = OkHttpClient.Builder().addInterceptor { chain -> response(chain.request(), body.get()) }.build()
        val server = ProxyServer(upstream)
        val receiver = OkHttpClient()
        try {
            server.start("budget-test", "127.0.0.1", unwrapWrapperPlaylists = unwrap, flattenHlsToStream = flatten)
            val id = server.registerPlaylist(ORIGIN)
            val request = Request.Builder().url(server.buildLocalUrl(id)).build()
            // Successful manifest responses are finite; live flattened streams are checked separately.
            if (!flatten) receiver.newCall(request).execute().use { assertEquals(200, it.code); it.body.string() }
            val leases = server.resourcesForTesting()
            body.set(hostile)
            receiver.newCall(request).execute().use { assertEquals(502, it.code) }
            assertEquals(leases, server.resourcesForTesting())
            body.set(healthy)
            if (!flatten) receiver.newCall(request).execute().use { assertEquals(200, it.code); it.body.string() }
        } finally {
            server.stop()
        }
    }

    @Test fun `flatten refresh parser rejects without headers or segment fetch`() {
        var calls = 0
        val upstream = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            response(chain.request(), hostile)
        }.build()
        val stream = HlsFlattenedStream(upstream, ORIGIN, "Agent", null, { true })
        try {
            assertFalse(stream.writeTo(ByteArrayOutputStream()) { error("No successful response may be committed") })
            assertEquals(1, calls)
        } finally {
            stream.stop()
        }
    }

    private fun response(request: Request, body: String) = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .header("Content-Type", "application/vnd.apple.mpegurl").body(body.toResponseBody()).build()

    private companion object { const val ORIGIN = "https://origin.invalid/live.m3u8" }
}
