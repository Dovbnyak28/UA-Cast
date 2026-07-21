package com.uacastplayer.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val BASE = "http://origin.example/get.php?user=u&pass=p"

class PlaylistUnwrapPolicyTest {

    @Test
    fun `unwraps the classic IPTV wrapper - EXTM3U plus one endless TS URL`() {
        val wrapper = "#EXTM3U\n#EXTINF:-1,Channel One\nhttp://origin.example/live/stream"
        assertEquals("http://origin.example/live/stream", PlaylistUnwrapPolicy.unwrapTarget(wrapper, BASE))
    }

    @Test
    fun `resolves a relative wrapped URL against the playlist URL`() {
        val wrapper = "#EXTM3U\n#EXTINF:-1,Channel\nstream/12345.ts"
        assertEquals(
            "http://origin.example/stream/12345.ts",
            PlaylistUnwrapPolicy.unwrapTarget(wrapper, "http://origin.example/playlist.m3u8"),
        )
    }

    @Test
    fun `a real media playlist is never unwrapped - TARGETDURATION marks it`() {
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:100
            #EXTINF:9.6,
            seg100.ts
        """.trimIndent()
        assertNull(PlaylistUnwrapPolicy.unwrapTarget(media, BASE))
    }

    @Test
    fun `a master playlist is never unwrapped`() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nvariant.m3u8"
        assertNull(PlaylistUnwrapPolicy.unwrapTarget(master, BASE))
    }

    @Test
    fun `a wrapper pointing at another playlist is left to the normal rewrite path`() {
        val wrapper = "#EXTM3U\n#EXTINF:-1,Channel\nhttp://origin.example/inner.m3u8"
        assertNull(PlaylistUnwrapPolicy.unwrapTarget(wrapper, BASE))
    }

    @Test
    fun `a multi-source wrapper unwraps to its first stream URL`() {
        // Main + backup source list - the field case that first shipped as single-URI-only and
        // silently fell back to the broken rewrite path.
        val playlist = "#EXTM3U\n#EXTINF:-1,Ch\nhttp://origin.example/a/stream\n" +
            "#EXTINF:-1,Ch\nhttp://backup.example/b/stream"
        assertEquals("http://origin.example/a/stream", PlaylistUnwrapPolicy.unwrapTarget(playlist, BASE))
    }

    @Test
    fun `a mixed list with any playlist URL is not treated as a wrapper`() {
        val playlist = "#EXTM3U\nhttp://origin.example/a.ts\nhttp://origin.example/b.m3u8"
        assertNull(PlaylistUnwrapPolicy.unwrapTarget(playlist, BASE))
    }

    @Test
    fun `an empty or comment-only playlist yields nothing to unwrap`() {
        assertNull(PlaylistUnwrapPolicy.unwrapTarget("#EXTM3U\n", BASE))
        assertNull(PlaylistUnwrapPolicy.unwrapTarget("", BASE))
    }
}
