package com.uacastplayer.data.cast

import com.uacastplayer.core.cast.CastRouteKind
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyRouteSelectorTest {

    @Test
    fun `a real playlist takes the playlist serving path`() {
        val decision = ProxyRouteSelector.select(
            topLevelResource(),
            response("#EXTM3U\n#EXTINF:4,\na.ts\n".toByteArray(), "application/vnd.apple.mpegurl"),
            remuxEnabled = true,
        )

        assertEquals(UpstreamRoute.PLAYLIST, decision.route)
        assertEquals(CastRouteKind.PROXY_REWRITE, decision.attemptedRoute)
    }

    @Test
    fun `a top-level raw TS stream takes the remux path`() {
        val decision = ProxyRouteSelector.select(
            topLevelResource(),
            response(tsBytes(), "video/mp2t"),
            remuxEnabled = true,
        )

        assertEquals(UpstreamRoute.REMUX, decision.route)
        assertEquals(CastRouteKind.PROXY_REMUX, decision.attemptedRoute)
    }

    @Test
    fun `a nested TS segment is passed through and is not counted as a route attempt`() {
        val decision = ProxyRouteSelector.select(
            topLevelResource().copy(type = RESOURCE_TYPE_MEDIA),
            response(tsBytes(), "video/mp2t"),
            remuxEnabled = true,
        )

        assertEquals(UpstreamRoute.PASSTHROUGH, decision.route)
        assertNull(decision.attemptedRoute)
    }

    @Test
    fun `disabling remux keeps raw TS on passthrough`() {
        val decision = ProxyRouteSelector.select(
            topLevelResource(),
            response(tsBytes(), "video/mp2t"),
            remuxEnabled = false,
        )

        assertEquals(UpstreamRoute.PASSTHROUGH, decision.route)
        assertEquals(CastRouteKind.PROXY_REWRITE, decision.attemptedRoute)
    }

    @Test
    fun `an unsuccessful HLS response is passed through without rewriting it to 200`() {
        val decision = ProxyRouteSelector.select(
            topLevelResource(),
            response(
                body = "Access denied".toByteArray(),
                contentType = "application/vnd.apple.mpegurl",
                code = 403,
            ),
            remuxEnabled = true,
        )

        assertEquals(UpstreamRoute.PASSTHROUGH, decision.route)
        assertEquals(CastRouteKind.PROXY_REWRITE, decision.attemptedRoute)
    }

    private fun topLevelResource() = ResourceEntry(
        type = RESOURCE_TYPE_PLAYLIST,
        originalUrl = "https://origin.example/live",
        userAgent = "UA Cast Test",
        referrer = null,
    )

    private fun response(body: ByteArray, contentType: String, code: Int = 200): Response = Response.Builder()
        .request(Request.Builder().url("https://origin.example/live").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code == 200) "OK" else "Forbidden")
        .header("Content-Type", contentType)
        .body(body.toResponseBody())
        .build()

    private fun tsBytes(): ByteArray = ByteArray(188 * 3).also { bytes ->
        for (offset in bytes.indices step 188) bytes[offset] = 0x47
    }
}
