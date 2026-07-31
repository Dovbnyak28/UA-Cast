package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSourcePolicyTest {

    private fun source(id: String, addedAt: Long = 0L) = PlaylistSource(
        id = id,
        type = PlaylistSourceType.URL,
        location = "http://$id",
        displayName = id,
        addedAtEpochMillis = addedAt,
    )

    @Test
    fun `adds a new source below the limit`() {
        val result = PlaylistSourcePolicy.add(listOf(source("a")), source("b"))
        assertTrue(result is PlaylistSourceAddResult.Added)
        assertEquals(listOf("a", "b"), (result as PlaylistSourceAddResult.Added).sources.map { it.id })
    }

    @Test
    fun `re-adding the same id replaces the existing entry instead of duplicating`() {
        val existing = listOf(source("a", addedAt = 1L))
        val result = PlaylistSourcePolicy.add(existing, source("a", addedAt = 2L))
        val added = result as PlaylistSourceAddResult.Added
        assertEquals(1, added.sources.size)
        assertEquals(2L, added.sources[0].addedAtEpochMillis)
    }

    @Test
    fun `replacing an existing entry is allowed even when already at the limit`() {
        val existing = (1..PlaylistSourcePolicy.MAX_SOURCES).map { source("s$it") }
        val result = PlaylistSourcePolicy.add(existing, source("s1", addedAt = 99L))
        assertTrue(result is PlaylistSourceAddResult.Added)
        assertEquals(PlaylistSourcePolicy.MAX_SOURCES, (result as PlaylistSourceAddResult.Added).sources.size)
    }

    @Test
    fun `adding a genuinely new source at the limit is rejected`() {
        val existing = (1..PlaylistSourcePolicy.MAX_SOURCES).map { source("s$it") }
        val result = PlaylistSourcePolicy.add(existing, source("new"))
        assertEquals(PlaylistSourceAddResult.LimitReached, result)
    }

    @Test
    fun `removing a non-active source leaves the active id untouched`() {
        val sources = listOf(source("a"), source("b"))
        val result = PlaylistSourcePolicy.remove(sources, activeId = "a", idToRemove = "b")
        val removed = result as PlaylistSourceRemovalResult.Removed
        assertEquals(listOf("a"), removed.sources.map { it.id })
        assertEquals("a", removed.newActiveId)
    }

    @Test
    fun `removing the active source falls back to the most recently added remaining one`() {
        val sources = listOf(source("a", addedAt = 1L), source("b", addedAt = 2L), source("c", addedAt = 3L))
        val result = PlaylistSourcePolicy.remove(sources, activeId = "a", idToRemove = "a")
        val removed = result as PlaylistSourceRemovalResult.Removed
        assertEquals("c", removed.newActiveId)
    }

    @Test
    fun `removing the last remaining source leaves no active id`() {
        val result = PlaylistSourcePolicy.remove(listOf(source("a")), activeId = "a", idToRemove = "a")
        val removed = result as PlaylistSourceRemovalResult.Removed
        assertTrue(removed.sources.isEmpty())
        assertNull(removed.newActiveId)
    }

    @Test
    fun `removing an id that is not in the list is reported as not found`() {
        val result = PlaylistSourcePolicy.remove(listOf(source("a")), activeId = "a", idToRemove = "missing")
        assertEquals(PlaylistSourceRemovalResult.NotFound, result)
    }
}
