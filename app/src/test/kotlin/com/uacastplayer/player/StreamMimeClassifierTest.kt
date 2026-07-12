package com.uacastplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamMimeClassifierTest {

    @Test
    fun `explicit m3u8 extension classifies as HLS`() {
        assertEquals(StreamType.HLS, StreamMimeClassifier.classify("http://example.com/stream.m3u8"))
    }

    @Test
    fun `explicit mpd extension classifies as DASH`() {
        assertEquals(StreamType.DASH, StreamMimeClassifier.classify("http://example.com/stream.mpd"))
    }

    @Test
    fun `url with no recognizable extension defaults to HLS`() {
        assertEquals(StreamType.HLS, StreamMimeClassifier.classify("http://example.com/live/channel1"))
    }

    @Test
    fun `mpd extension followed by query string still classifies as DASH`() {
        assertEquals(StreamType.DASH, StreamMimeClassifier.classify("http://example.com/stream.mpd?token=abc"))
    }

    @Test
    fun `m3u8 extension followed by fragment still classifies as HLS`() {
        assertEquals(StreamType.HLS, StreamMimeClassifier.classify("http://example.com/stream.m3u8#t=10"))
    }

    @Test
    fun `classification is case insensitive`() {
        assertEquals(StreamType.DASH, StreamMimeClassifier.classify("http://example.com/STREAM.MPD"))
    }

    @Test
    fun `query string containing mpd-like text does not trigger DASH when path has no extension`() {
        assertEquals(StreamType.HLS, StreamMimeClassifier.classify("http://example.com/play?format=mpd"))
    }
}
