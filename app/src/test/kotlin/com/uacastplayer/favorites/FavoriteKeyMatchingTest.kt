package com.uacastplayer.favorites

import com.uacastplayer.playlist.M3uChannel
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteKeyMatchingTest {
    @Test fun `matching agrees with original keys for arbitrary identifier and name shapes`() {
        val random = Random(337)
        val names = listOf("", "A", "A:B", "Новини:HD", "Niño", "İ", " padded ", "a")
        val ids = listOf(null, "", " ", "\t\n", "\u00a0", "id", "A", "A:B")
        repeat(2_000) { index ->
            val channel = M3uChannel(
                names.random(random), "https://unused.example.test/$index", tvgId = ids.random(random),
            )
            val original = FavoriteKey.of(channel)
            val keys = listOf(original, "", "missing", original.uppercase(), original + "x", "A:invalid", "A:B")
            for (key in keys) assertEquals(original == key, FavoriteKey.matches(channel, key))
        }
    }

    @Test fun `same names still require exact URL fingerprints`() {
        val channel = M3uChannel("News:HD", "https://unused.example.test/one")
        val other = channel.copy(streamUrl = "https://unused.example.test/two")
        assertTrue(FavoriteKey.matches(channel, FavoriteKey.of(channel)))
        assertFalse(FavoriteKey.matches(other, FavoriteKey.of(channel)))
        assertFalse(FavoriteKey.matches(channel.copy(displayName = "News"), FavoriteKey.of(channel)))
        assertFalse(FavoriteKey.matches(channel.copy(displayName = "news:HD"), FavoriteKey.of(channel)))
    }

    @Test fun `explicit IDs that look like fallback keys are not reinterpreted`() {
        val fallback = M3uChannel("News", "https://unused.example.test/live")
        val key = FavoriteKey.of(fallback)
        assertTrue(FavoriteKey.matches(fallback.copy(displayName = "Different", tvgId = key), key))
        assertFalse(FavoriteKey.matches(fallback.copy(tvgId = "different-id"), key))
    }
}
