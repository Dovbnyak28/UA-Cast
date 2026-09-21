package com.uacastplayer.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IconMemoryCacheKeyTest {

    @Test
    fun `identical inputs produce the same key`() {
        val first = IconMemoryCacheKey.of("https://example.com/logo.png", "https://example.com/epg.png", "id")
        val second = IconMemoryCacheKey.of("https://example.com/logo.png", "https://example.com/epg.png", "id")

        assertEquals(first, second)
    }

    @Test
    fun `different tvgLogo produces a different key`() {
        val first = IconMemoryCacheKey.of("https://example.com/logo-a.png", "https://example.com/epg.png", "id")
        val second = IconMemoryCacheKey.of("https://example.com/logo-b.png", "https://example.com/epg.png", "id")

        assertNotEquals(first, second)
    }

    @Test
    fun `a value shifted across the field boundary does not collide`() {
        val first = IconMemoryCacheKey.of("https://example.com/ab.png", null, null)
        val second = IconMemoryCacheKey.of("https://example.com/a.png", "https://example.com/b.png", null)

        assertNotEquals(first, second)
    }

    @Test
    fun `all-null inputs still produce a stable key`() {
        val first = IconMemoryCacheKey.of(null, null, null)
        val second = IconMemoryCacheKey.of(null, null, null)

        assertEquals(first, second)
    }

    @Test
    fun `equivalent provider URL spellings share a key`() {
        val first = IconMemoryCacheKey.of(
            " //example.com/logo.png?token=a&amp;v=2 ",
            null,
            "id",
        )
        val second = IconMemoryCacheKey.of(
            "https://example.com/logo.png?token=a&v=2",
            null,
            "id",
        )

        assertEquals(first, second)
    }

    @Test
    fun `malformed URL fields match the resolver's skipped candidate`() {
        val first = IconMemoryCacheKey.of("not a URL", null, "id")
        val second = IconMemoryCacheKey.of(null, null, "id")

        assertEquals(first, second)
    }
}
