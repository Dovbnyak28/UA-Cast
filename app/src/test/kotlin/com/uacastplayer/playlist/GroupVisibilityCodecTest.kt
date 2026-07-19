package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupVisibilityCodecTest {

    @Test
    fun `round-trips a pinned and a hidden entry`() {
        val entries = listOf(
            GroupVisibilityEntry("source-1", "known:sports", GroupVisibilityState.PINNED),
            GroupVisibilityEntry("source-1", "custom:Local News", GroupVisibilityState.HIDDEN),
        )
        assertEquals(entries, GroupVisibilityCodec.decode(GroupVisibilityCodec.encode(entries)))
    }

    @Test
    fun `entries are scoped per source id in the round trip`() {
        val entries = listOf(
            GroupVisibilityEntry("source-1", "known:sports", GroupVisibilityState.PINNED),
            GroupVisibilityEntry("source-2", "known:sports", GroupVisibilityState.HIDDEN),
        )
        assertEquals(entries, GroupVisibilityCodec.decode(GroupVisibilityCodec.encode(entries)))
    }

    @Test
    fun `round-trips an empty list`() {
        val encoded = GroupVisibilityCodec.encode(emptyList())
        assertEquals(emptyList<GroupVisibilityEntry>(), GroupVisibilityCodec.decode(encoded))
    }

    @Test
    fun `decoding malformed JSON returns an empty list instead of throwing`() {
        assertTrue(GroupVisibilityCodec.decode("{not valid").isEmpty())
    }

    @Test
    fun `decoding an entry with an unrecognized state is dropped`() {
        val json = """[{"sourceId":"s","groupKey":"g","state":"BOGUS"}]"""
        assertTrue(GroupVisibilityCodec.decode(json).isEmpty())
    }

    @Test
    fun `decoding an entry missing a required field is dropped`() {
        val json = """[{"sourceId":"s","state":"PINNED"}]"""
        assertTrue(GroupVisibilityCodec.decode(json).isEmpty())
    }
}
