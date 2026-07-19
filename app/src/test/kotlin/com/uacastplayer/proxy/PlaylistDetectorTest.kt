package com.uacastplayer.proxy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistDetectorTest {

    @Test
    fun `recognizes a playlist mime type regardless of extension hints`() {
        assertTrue(PlaylistDetector.isPlaylist("application/vnd.apple.mpegurl", ByteArray(0)))
        assertTrue(PlaylistDetector.isPlaylist("application/x-mpegURL; charset=utf-8", ByteArray(0)))
        assertTrue(PlaylistDetector.isPlaylist("audio/mpegurl", ByteArray(0)))
    }

    @Test
    fun `recognizes the EXTM3U magic bytes when content type is generic`() {
        val body = "#EXTM3U\n#EXTINF:-1,Channel\nsegment1.ts".toByteArray(Charsets.UTF_8)
        assertTrue(PlaylistDetector.isPlaylist("application/octet-stream", body))
    }

    @Test
    fun `recognizes EXTM3U past a leading UTF-8 BOM`() {
        val body = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "#EXTM3U\n".toByteArray(Charsets.UTF_8)
        assertTrue(PlaylistDetector.isPlaylist(null, body))
    }

    @Test
    fun `a media segment with no playlist signal is not a playlist`() {
        val body = byteArrayOf(0x47, 0x40, 0x00, 0x10) // MPEG-TS sync byte, not EXTM3U
        assertFalse(PlaylistDetector.isPlaylist("video/mp2t", body))
    }

    @Test
    fun `an error page served with a generic content type is not mistaken for a playlist`() {
        val body = "<html><body>403 Forbidden</body></html>".toByteArray(Charsets.UTF_8)
        assertFalse(PlaylistDetector.isPlaylist("text/html", body))
    }

    @Test
    fun `an empty or truncated body is not a playlist`() {
        assertFalse(PlaylistDetector.isPlaylist("application/octet-stream", ByteArray(0)))
        assertFalse(PlaylistDetector.isPlaylist("application/octet-stream", "#EXTM".toByteArray()))
    }

    @Test
    fun `content type is not required when the magic bytes are present`() {
        assertTrue(PlaylistDetector.isPlaylist(null, "#EXTM3U".toByteArray()))
    }
}
