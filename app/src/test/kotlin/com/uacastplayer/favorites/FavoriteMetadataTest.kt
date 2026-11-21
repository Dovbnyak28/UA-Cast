package com.uacastplayer.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteMetadataTest {
    @Test fun `legacy favorites recover access metadata from identical streams only`() {
        val favorite = FavoriteChannel("id", "Name", "https://a/live", "id", null)
        val channel = favorite.toChannel().copy(tvgLogo = "https://a/logo", userAgent = "A", referrer = "https://a/")
        val restored = FavoriteMetadata.enrich(listOf(favorite), listOf(channel)).single()
        assertEquals(channel, restored.toChannel())
        val otherProvider = channel.copy(streamUrl = "https://b/live")
        val untouched = FavoriteMetadata.enrich(listOf(favorite), listOf(otherProvider)).single()
        assertNull(untouched.userAgent)
        assertNull(untouched.tvgLogo)
    }
}
