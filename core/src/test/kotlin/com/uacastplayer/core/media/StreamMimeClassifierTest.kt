package com.uacastplayer.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamMimeClassifierTest {

    @Test
    fun `m3u8 extension is HLS`() {
        assertEquals(StreamType.HLS, StreamMimeClassifier.classify("http://example.com/stream.m3u8"))
    }

    @Test
    fun `mpd extension is DASH`() {
        assertEquals(StreamType.DASH, StreamMimeClassifier.classify("http://example.com/stream.mpd"))
    }

    @Test
    fun `extensionless URL defaults to HLS`() {
        assertEquals(StreamType.HLS, StreamMimeClassifier.classify("http://example.com/live/channel1"))
    }

    @Test
    fun `query string does not hide DASH extension`() {
        assertEquals(StreamType.DASH, StreamMimeClassifier.classify("http://example.com/stream.mpd?token=abc"))
    }

    @Test
    fun `fragment does not hide HLS extension`() {
        assertEquals(StreamType.HLS, StreamMimeClassifier.classify("http://example.com/stream.m3u8#t=10"))
    }

    @Test
    fun `extension matching is case insensitive`() {
        assertEquals(StreamType.DASH, StreamMimeClassifier.classify("http://example.com/STREAM.MPD"))
    }

    @Test
    fun `mpd query parameter alone is not treated as DASH`() {
        assertEquals(StreamType.HLS, StreamMimeClassifier.classify("http://example.com/play?format=mpd"))
    }
}
