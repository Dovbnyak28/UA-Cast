package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class CastContentTypeTest {

    @Test
    fun `Hls sourceKind maps to the HLS mime type`() {
        assertEquals("application/x-mpegurl", CastContentType.of("http://origin/stream", TsSourceKind.Hls))
    }

    @Test
    fun `RawTs sourceKind maps to video mp2t`() {
        assertEquals("video/mp2t", CastContentType.of("http://origin/stream", TsSourceKind.RawTs))
    }

    @Test
    fun `Unknown sourceKind falls back to the URL guess`() {
        assertEquals("application/x-mpegurl", CastContentType.of("http://origin/stream", TsSourceKind.Unknown))
        assertEquals("application/dash+xml", CastContentType.of("http://origin/stream.mpd", TsSourceKind.Unknown))
    }

    @Test
    fun `null sourceKind falls back to the URL guess, same as Unknown`() {
        assertEquals("application/x-mpegurl", CastContentType.of("http://origin/stream", null))
    }

    @Test
    fun `our own proxy URL is always HLS regardless of sourceKind`() {
        val proxyUrl = "http://192.168.1.5:8080/hls/token/resource"
        assertEquals("application/x-mpegurl", CastContentType.of(proxyUrl, TsSourceKind.RawTs))
        assertEquals("application/x-mpegurl", CastContentType.of(proxyUrl, null))
        assertEquals("application/x-mpegurl", CastContentType.of(proxyUrl, TsSourceKind.Unknown))
    }
}
