package com.uacastplayer.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IconMemoryCacheKeyTest {

    @Test
    fun `identical inputs produce the same key`() {
        val first = IconMemoryCacheKey.of("logo", "epg", "id")
        val second = IconMemoryCacheKey.of("logo", "epg", "id")

        assertEquals(first, second)
    }

    @Test
    fun `different tvgLogo produces a different key`() {
        val first = IconMemoryCacheKey.of("logo-a", "epg", "id")
        val second = IconMemoryCacheKey.of("logo-b", "epg", "id")

        assertNotEquals(first, second)
    }

    @Test
    fun `a value shifted across the field boundary does not collide`() {
        val first = IconMemoryCacheKey.of("ab", null, null)
        val second = IconMemoryCacheKey.of("a", "b", null)

        assertNotEquals(first, second)
    }

    @Test
    fun `all-null inputs still produce a stable key`() {
        val first = IconMemoryCacheKey.of(null, null, null)
        val second = IconMemoryCacheKey.of(null, null, null)

        assertEquals(first, second)
    }
}
