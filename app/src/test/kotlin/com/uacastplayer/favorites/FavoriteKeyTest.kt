package com.uacastplayer.favorites

import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FavoriteKeyTest {

    @Test
    fun `uses tvg-id when present`() {
        val channel = M3uChannel(displayName = "BBC One", streamUrl = "http://x/1", tvgId = "bbc.one.uk")
        assertEquals("bbc.one.uk", FavoriteKey.of(channel))
    }

    @Test
    fun `falls back to name plus stream URL hash when tvg-id is absent`() {
        val channel = M3uChannel(displayName = "BBC One", streamUrl = "http://x/1")
        val key = FavoriteKey.of(channel)
        assertEquals(true, key.startsWith("BBC One:"))
        assertNotEquals("BBC One:", key)
    }

    @Test
    fun `falls back to name plus hash when tvg-id is blank`() {
        val channel = M3uChannel(displayName = "BBC One", streamUrl = "http://x/1", tvgId = "   ")
        assertEquals(true, FavoriteKey.of(channel).startsWith("BBC One:"))
    }

    @Test
    fun `two channels with the same name but different URLs get different fallback keys`() {
        val a = M3uChannel(displayName = "News", streamUrl = "http://x/a")
        val b = M3uChannel(displayName = "News", streamUrl = "http://x/b")
        assertNotEquals(FavoriteKey.of(a), FavoriteKey.of(b))
    }

    @Test
    fun `the same channel always produces the same key`() {
        val channel = M3uChannel(displayName = "News", streamUrl = "http://x/a", tvgId = "news.1")
        assertEquals(FavoriteKey.of(channel), FavoriteKey.of(channel.copy()))
    }
}
