package com.uacastplayer.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemFactoryTest {
    @Test fun `explicit progressive Cast media retains a precise MIME type`() {
        assertEquals("video/mp4", com.uacastplayer.cast.CastContentType.of("https://x/a.mp4?token=a", null))
        assertEquals("audio/mpeg", com.uacastplayer.cast.CastContentType.of("https://x/a.mp3", null))
        assertEquals("video/mp2t", com.uacastplayer.cast.CastContentType.of("https://x/a.ts", null))
    }

    @Test fun `explicit TS and progressive media are not forced through HLS parser`() {
        listOf("https://x/a.TS?token=a", "https://x/a.m2ts", "https://x/a.mp4", "https://x/a.mp3")
            .forEach { assertNull(MediaItemFactory.forChannel(it).localConfiguration!!.mimeType) }
    }

    @Test fun `adaptive and ambiguous provider URLs keep adaptive initial handling`() {
        assertEquals(
            MimeTypes.APPLICATION_MPD, MediaItemFactory.forChannel("https://x/a.mpd").localConfiguration!!.mimeType,
        )
        listOf("https://x/a.m3u8", "https://x/live/123").forEach {
            assertEquals(MimeTypes.APPLICATION_M3U8, MediaItemFactory.forChannel(it).localConfiguration!!.mimeType)
        }
    }

    @Test fun `ambiguous TS can fall back exactly once without changing the signed URL`() {
        val url = "https://x/live/123?token=abc"
        val item = MediaItemFactory.forChannel(url)
        val replacement = MediaItemFactory.progressiveFallback(
            item, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        )
        assertNotNull(replacement)
        assertEquals(url, replacement!!.localConfiguration!!.uri.toString())
        assertNull(MediaItemFactory.progressiveFallback(
            replacement, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        ))
    }

    @Test fun `real manifest corruption and HTTP errors do not trigger format fallback`() {
        assertNull(MediaItemFactory.progressiveFallback(
            MediaItemFactory.forChannel("https://x/a.m3u8"), PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        ))
        assertNull(MediaItemFactory.progressiveFallback(
            MediaItemFactory.forChannel("https://x/live/123"), PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        ))
    }
}
