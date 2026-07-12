package com.uacastplayer.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconCacheTrimmerTest {

    private fun entry(key: String, sizeBytes: Long, lastAccessed: Long) = CacheEntry(key, sizeBytes, lastAccessed)

    @Test
    fun `evicts nothing when already under both limits`() {
        val entries = listOf(entry("a", 100, 1), entry("b", 100, 2))
        val result = IconCacheTrimmer.selectEntriesToEvict(entries, maxTotalBytes = 1000, maxCount = 10)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `evicts oldest-accessed entries first to satisfy a byte limit`() {
        val entries = listOf(
            entry("oldest", 50, lastAccessed = 1),
            entry("middle", 50, lastAccessed = 2),
            entry("newest", 50, lastAccessed = 3),
        )
        val result = IconCacheTrimmer.selectEntriesToEvict(entries, maxTotalBytes = 100, maxCount = 10)
        assertEquals(listOf("oldest"), result.map { it.key })
    }

    @Test
    fun `evicts enough entries to satisfy a count limit`() {
        val entries = (1..5).map { entry("ch$it", sizeBytes = 10, lastAccessed = it.toLong()) }
        val result = IconCacheTrimmer.selectEntriesToEvict(entries, maxTotalBytes = 10_000, maxCount = 3)
        assertEquals(listOf("ch1", "ch2"), result.map { it.key })
    }

    @Test
    fun `stops evicting as soon as both limits are satisfied`() {
        val entries = listOf(
            entry("a", 100, 1),
            entry("b", 100, 2),
            entry("c", 100, 3),
        )
        val result = IconCacheTrimmer.selectEntriesToEvict(entries, maxTotalBytes = 250, maxCount = 10)
        assertEquals(listOf("a"), result.map { it.key })
    }

    @Test
    fun `empty cache evicts nothing`() {
        assertTrue(IconCacheTrimmer.selectEntriesToEvict(emptyList()).isEmpty())
    }
}
