package com.uacastplayer.parentalcontrol

import com.uacastplayer.core.json.JsonDecodeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedChannelsCodecTest {

    @Test
    fun `round-trips a set of channel keys`() {
        val keys = setOf("tvg-id-1", "Some Channel:abcd1234")
        assertEquals(keys, LockedChannelsCodec.decode(LockedChannelsCodec.encode(keys)))
    }

    @Test
    fun `round-trips an empty set`() {
        assertEquals(emptySet<String>(), LockedChannelsCodec.decode(LockedChannelsCodec.encode(emptySet())))
    }

    @Test
    fun `decoding malformed JSON returns an empty set instead of throwing`() {
        assertTrue(LockedChannelsCodec.decode("{not valid").isEmpty())
        assertTrue(LockedChannelsCodec.decodeResult("{not valid") is JsonDecodeResult.Malformed)
    }

    @Test
    fun `decoding a record missing the channelKey field is dropped`() {
        assertTrue(LockedChannelsCodec.decode("""[{"other":"value"}]""").isEmpty())
    }

    @Test
    fun `duplicate keys collapse to one entry`() {
        val json = """[{"channelKey":"tvg-id-1"},{"channelKey":"tvg-id-1"}]"""
        assertEquals(setOf("tvg-id-1"), LockedChannelsCodec.decode(json))
    }
}
