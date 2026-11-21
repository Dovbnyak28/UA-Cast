package com.uacastplayer.cast

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CastMediaLoaderTest {

    @Test fun `a missing sdk snapshot cannot prove file completion`() {
        val video = com.uacastplayer.media.LocalVideo("content://media/video/1", "Movie.mp4", "video/mp4")
        assertFalse(bufferedVideoFinished(CastChannel(0, video.uri, video.displayName, localVideo = video), null))
    }

    @Test fun `a previous file load status cannot be accepted by a replay of the same proxy url`() {
        val video = com.uacastplayer.media.LocalVideo("content://media/video/1", "Movie.mp4", "video/mp4")
        val channel = CastChannel(0, video.uri, video.displayName, localVideo = video)
        val url = "http://192.0.2.1:8080/hls/token/id"
        val previous = CastMediaLoader.forPlayback(url, video.displayName, channel, null).mediaInfo!!
        val replacement = CastMediaLoader.forPlayback(url, video.displayName, channel, null).mediaInfo!!
        assertEquals(url, previous.contentUrl)
        assertEquals(url, replacement.contentUrl)
        assertFalse(CastStatusContentPolicy.shouldAccept(previous.contentId, replacement.contentId))
        assertTrue(CastStatusContentPolicy.shouldAccept(replacement.contentId, replacement.contentId))
    }

    @Test fun `file completion is normal even when it is the first status after load`() {
        val video = com.uacastplayer.media.LocalVideo("content://media/video/1", "Short.mp4", "video/mp4")
        val channel = CastChannel(0, video.uri, video.displayName, localVideo = video)
        assertTrue(bufferedVideoFinished(channel, ReceiverStatus.IDLE, IdleReason.FINISHED))
        val completed = CastPlaybackState(loadPhase = CastLoadPhase.LOADING).withBufferedVideoFinished()
        assertTrue(completed.isSessionConnected)
        assertEquals(CastLoadPhase.LOADED, completed.loadPhase)
        assertEquals(IdleReason.FINISHED, completed.idleReason)
        assertFalse(completed.receiverLoadFailed)
    }

    @Test fun `live loads retain the unmodified provider identity and url`() {
        val url = "https://origin.example/live.m3u8?token=signed-value"
        val channel = CastChannel(0, url, "Live")
        repeat(2) {
            val info = CastMediaLoader.forPlayback(url, channel.title, channel, null).mediaInfo!!
            assertEquals(url, info.contentId)
            assertEquals(url, info.contentUrl)
            assertEquals(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_LIVE, info.streamType)
        }
    }

    @Test fun `a local file uses buffered metadata and its real mime despite the opaque proxy url`() {
        val video = com.uacastplayer.media.LocalVideo("content://media/video/1", "Movie.mkv", "video/x-matroska")
        val channel = CastChannel(0, video.uri, video.displayName, localVideo = video)
        val info = CastMediaLoader.forPlayback(
            "http://192.0.2.1:8080/hls/token/id", video.displayName, channel, null,
        ).mediaInfo!!
        assertEquals(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED, info.streamType)
        assertEquals("video/x-matroska", info.contentType)
        assertEquals("http://192.0.2.1:8080/hls/token/id", info.contentUrl)
    }

    @Test fun `normal file completion is not a live channel recovery event`() {
        val video = com.uacastplayer.media.LocalVideo("content://media/video/1", "Movie.mp4", "video/mp4")
        val channel = CastChannel(0, video.uri, video.displayName, localVideo = video)
        assertTrue(bufferedVideoFinished(channel, ReceiverStatus.IDLE, IdleReason.FINISHED))
        assertFalse(bufferedVideoFinished(channel, ReceiverStatus.IDLE, IdleReason.ERROR))
        assertFalse(bufferedVideoFinished(CastChannel(0, "https://test/live", "Live"),
            ReceiverStatus.IDLE, IdleReason.FINISHED))
    }

    private fun imagesOf(logoUrl: String?) =
        CastMediaLoader.buildRequest(
            streamUrl = "https://origin.example/stream.m3u8",
            title = "Channel",
            logoUrl = logoUrl,
        ).mediaInfo?.metadata?.images.orEmpty()

    @Test
    fun `a channel logo becomes the receiver's artwork`() {
        val images = imagesOf("https://logos.example/channel.png")
        assertEquals(1, images.size)
        assertEquals("https://logos.example/channel.png", images.single().url.toString())
    }

    /** An empty Uri is worse than none: the receiver reports a broken image as an error rather
     * than falling back to its no-artwork layout. */
    @Test
    fun `a blank logo is dropped rather than sent as an empty image`() {
        assertTrue(imagesOf("   ").isEmpty())
    }

    @Test
    fun `a channel with no logo at all sends no image`() {
        assertTrue(imagesOf(null).isEmpty())
    }

    @Test
    fun `the title still reaches the receiver alongside the artwork`() {
        val request = CastMediaLoader.buildRequest(
            streamUrl = "https://origin.example/stream.m3u8",
            title = "Viasat Serial HD",
            logoUrl = "https://logos.example/channel.png",
        )
        assertEquals(
            "Viasat Serial HD",
            request.mediaInfo?.metadata?.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE),
        )
    }
}
