package com.uacastplayer.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3u8RewriterTest {

    private val identityMap: (String) -> String? = { url -> "LOCAL(${url})" }

    @Test
    fun `rewrites a relative segment URI to an absolute local URL`() {
        val playlist = "#EXTM3U\n#EXTINF:10,\nsegment1.ts\n"
        val result = M3u8Rewriter.rewrite(playlist, "http://example.com/live/playlist.m3u8", identityMap)
        assertEquals(
            "#EXTM3U\n#EXTINF:10,\nLOCAL(http://example.com/live/segment1.ts)\n",
            result,
        )
    }

    @Test
    fun `rewrites an absolute segment URI`() {
        val playlist = "#EXTM3U\nhttp://other.com/segment1.ts"
        val result = M3u8Rewriter.rewrite(playlist, "http://example.com/live/playlist.m3u8", identityMap)
        assertEquals("#EXTM3U\nLOCAL(http://other.com/segment1.ts)", result)
    }

    @Test
    fun `rewrites the URI attribute on EXT-X-KEY`() {
        val playlist = "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\""
        val result = M3u8Rewriter.rewrite(playlist, "http://example.com/live/playlist.m3u8", identityMap)
        val expected = "#EXT-X-KEY:METHOD=AES-128,URI=\"LOCAL(http://example.com/live/key.bin)\""
        assertEquals(expected, result)
    }

    @Test
    fun `rewrites the URI attribute on EXT-X-MEDIA and EXT-X-MAP`() {
        val playlist = """
            #EXT-X-MEDIA:TYPE=SUBTITLES,URI="subs.m3u8"
            #EXT-X-MAP:URI="init.mp4"
        """.trimIndent()
        val result = M3u8Rewriter.rewrite(playlist, "http://example.com/live/playlist.m3u8", identityMap)
        assertEquals(
            "#EXT-X-MEDIA:TYPE=SUBTITLES,URI=\"LOCAL(http://example.com/live/subs.m3u8)\"\n" +
                "#EXT-X-MAP:URI=\"LOCAL(http://example.com/live/init.mp4)\"",
            result,
        )
    }

    @Test
    fun `leaves an unsupported scheme untouched`() {
        val playlist = """#EXT-X-KEY:METHOD=SAMPLE-AES,URI="skd://some-key-id""""
        val result = M3u8Rewriter.rewrite(playlist, "http://example.com/live/playlist.m3u8", identityMap)
        assertEquals(playlist, result)
    }

    @Test
    fun `passes through tags without a URI attribute unchanged`() {
        val playlist = "#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10"
        val result = M3u8Rewriter.rewrite(playlist, "http://example.com/live/playlist.m3u8", identityMap)
        assertEquals(playlist, result)
    }

    @Test
    fun `preserves blank lines`() {
        val playlist = "#EXTM3U\n\nsegment1.ts"
        val result = M3u8Rewriter.rewrite(playlist, "http://example.com/live/playlist.m3u8", identityMap)
        assertEquals("#EXTM3U\n\nLOCAL(http://example.com/live/segment1.ts)", result)
    }

    @Test
    fun `master playlist variant stream URIs are rewritten relative to the final URL after redirects`() {
        val playlist = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nvariant/low.m3u8"
        // Base URL simulates the final URL after a redirect to a different path.
        val result = M3u8Rewriter.rewrite(playlist, "http://cdn.example.com/redirected/master.m3u8", identityMap)
        assertEquals(
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nLOCAL(http://cdn.example.com/redirected/variant/low.m3u8)",
            result,
        )
    }

    @Test
    fun `resolveUrl resolves an absolute path against a base URL`() {
        assertEquals(
            "http://example.com/other/path.ts",
            M3u8Rewriter.resolveUrl("http://example.com/live/playlist.m3u8", "/other/path.ts"),
        )
    }

    @Test
    fun `resolveUrl returns null for a malformed reference`() {
        assertNull(M3u8Rewriter.resolveUrl("http://example.com/live/playlist.m3u8", "http://[::badipv6"))
    }

    @Test
    fun `mapUrl returning null leaves the original reference untouched`() {
        val playlist = "#EXTM3U\nsegment1.ts"
        val result = M3u8Rewriter.rewrite(playlist, "http://example.com/live/playlist.m3u8") { null }
        assertEquals(playlist, result)
    }
}
