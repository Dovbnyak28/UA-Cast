package com.uacastplayer.cast

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CastMediaLoaderTest {

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
