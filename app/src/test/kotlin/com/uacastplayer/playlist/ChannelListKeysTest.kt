package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChannelListKeysTest {

    @Test
    fun `same streamUrl at different indices produces different keys`() {
        val first = ChannelListKeys.keyFor(0, "http://example.com/stream")
        val second = ChannelListKeys.keyFor(1, "http://example.com/stream")

        assertNotEquals(first, second)
    }

    @Test
    fun `same index and streamUrl produces the same key`() {
        val first = ChannelListKeys.keyFor(3, "http://example.com/stream")
        val second = ChannelListKeys.keyFor(3, "http://example.com/stream")

        assertEquals(first, second)
    }

    @Test
    fun `different streamUrls at the same index produce different keys`() {
        val first = ChannelListKeys.keyFor(0, "http://example.com/a")
        val second = ChannelListKeys.keyFor(0, "http://example.com/b")

        assertNotEquals(first, second)
    }

    @Test
    fun `key is prefixed with the index`() {
        val key = ChannelListKeys.keyFor(7, "http://example.com/stream")

        assertEquals(true, key.startsWith("7:"))
    }
}
