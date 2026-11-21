package com.uacastplayer.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IconMemoryCacheKeyTest {
    @Test fun `identical channel IDs share a key`() {
        assertEquals(IconMemoryCacheKey.of("id"), IconMemoryCacheKey.of("id"))
    }

    @Test fun `different channel IDs never share a key`() {
        assertNotEquals(IconMemoryCacheKey.of("news"), IconMemoryCacheKey.of("sport"))
    }

    @Test fun `missing and blank IDs share the empty candidate result`() {
        assertEquals(IconMemoryCacheKey.of(null), IconMemoryCacheKey.of(" "))
    }

    @Test fun `literal null and delimiters are valid distinct IDs`() {
        assertNotEquals(IconMemoryCacheKey.of(null), IconMemoryCacheKey.of("null"))
        assertNotEquals(IconMemoryCacheKey.of("news|sport"), IconMemoryCacheKey.of("news"))
    }
}
