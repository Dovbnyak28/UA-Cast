package com.uacastplayer.favorites

import com.uacastplayer.core.json.JsonDecodeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesJsonCodecTest {

    @Test
    fun `round-trips a fully populated favorite`() {
        val favorites = listOf(
            FavoriteChannel(
                key = "bbc.one.uk",
                displayName = "BBC One",
                streamUrl = "http://example.com/1.m3u8",
                tvgId = "bbc.one.uk",
                groupTitle = "News",
            )
        )
        val json = FavoritesJsonCodec.encode(favorites)
        assertEquals(favorites, FavoritesJsonCodec.decode(json))
    }

    @Test
    fun `round-trips a favorite with null optional fields`() {
        val favorites = listOf(
            FavoriteChannel(
                key = "News:abc123",
                displayName = "News",
                streamUrl = "http://x/1",
                tvgId = null,
                groupTitle = null,
            )
        )
        val json = FavoritesJsonCodec.encode(favorites)
        assertEquals(favorites, FavoritesJsonCodec.decode(json))
    }

    @Test
    fun `round-trips an empty list`() {
        assertEquals(emptyList<FavoriteChannel>(), FavoritesJsonCodec.decode(FavoritesJsonCodec.encode(emptyList())))
    }

    @Test
    fun `round-trips multiple favorites preserving order`() {
        val favorites = (1..5).map {
            FavoriteChannel(
                key = "ch$it",
                displayName = "Channel $it",
                streamUrl = "http://x/$it",
                tvgId = null,
                groupTitle = null,
            )
        }
        assertEquals(favorites, FavoritesJsonCodec.decode(FavoritesJsonCodec.encode(favorites)))
    }

    @Test
    fun `decoding malformed JSON returns an empty list instead of throwing`() {
        assertTrue(FavoritesJsonCodec.decode("{not valid").isEmpty())
        assertTrue(FavoritesJsonCodec.decodeResult("{not valid") is JsonDecodeResult.Malformed)
    }

    @Test
    fun `decoding an entry missing a required field is dropped`() {
        val json = """[{"key":"a","displayName":"A"}]"""
        assertTrue(FavoritesJsonCodec.decode(json).isEmpty())
    }
}
