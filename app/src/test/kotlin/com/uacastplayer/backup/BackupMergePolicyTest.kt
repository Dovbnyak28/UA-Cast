package com.uacastplayer.backup

import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourcePolicy
import com.uacastplayer.playlist.PlaylistSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergePolicyTest {

    private fun favorite(key: String) = FavoriteChannel(key, "Channel $key", "http://x/$key", null, null, 0L)
    private fun backupFavorite(key: String) = BackupFavorite(key, "Channel $key", "http://x/$key", key, null, 0L)
    private fun source(name: String): PlaylistSource {
        val location = "http://$name"
        return PlaylistSource(Fingerprint.of(location), PlaylistSourceType.URL, location, "S$name", 0L)
    }

    private fun backupSource(name: String) = BackupPlaylistSource(
        Fingerprint.of("http://$name"),
        "URL",
        "http://$name",
        "S$name",
        0L,
    )

    @Test
    fun `imported favorites not already present are added`() {
        val result = BackupMergePolicy.merge(
            existingSources = emptyList(),
            existingFavorites = listOf(favorite("a")),
            importedSources = emptyList(),
            importedFavorites = listOf(backupFavorite("b")),
        )
        assertEquals(setOf("a", "b"), result.favorites.map { it.key }.toSet())
        assertEquals(1, result.importedFavoriteCount)
    }

    @Test
    fun `imported favorites already present by key are not duplicated`() {
        val result = BackupMergePolicy.merge(
            existingSources = emptyList(),
            existingFavorites = listOf(favorite("a")),
            importedSources = emptyList(),
            importedFavorites = listOf(backupFavorite("a")),
        )
        assertEquals(1, result.favorites.size)
        assertEquals(0, result.importedFavoriteCount)
    }

    @Test
    fun `duplicate keys within the imported batch itself are only added once`() {
        val result = BackupMergePolicy.merge(
            existingSources = emptyList(),
            existingFavorites = emptyList(),
            importedSources = emptyList(),
            importedFavorites = listOf(backupFavorite("a"), backupFavorite("a")),
        )
        assertEquals(1, result.favorites.size)
        assertEquals(1, result.importedFavoriteCount)
    }

    @Test
    fun `imported sources are added up to the limit`() {
        val existing = (1..PlaylistSourcePolicy.MAX_SOURCES).map { source("s$it") }
        val result = BackupMergePolicy.merge(
            existingSources = existing,
            existingFavorites = emptyList(),
            importedSources = listOf(backupSource("new")),
            importedFavorites = emptyList(),
        )
        assertEquals(existing, result.sources)
        assertEquals(0, result.importedSourceCount)
        assertEquals(1, result.sourceLimitExceededCount)
    }

    @Test
    fun `imported sources below the limit are all added`() {
        val result = BackupMergePolicy.merge(
            existingSources = listOf(source("a")),
            existingFavorites = emptyList(),
            importedSources = listOf(backupSource("b"), backupSource("c")),
            importedFavorites = emptyList(),
        )
        assertEquals(setOf(source("a").id, source("b").id, source("c").id), result.sources.map { it.id }.toSet())
        assertEquals(2, result.importedSourceCount)
        assertEquals(0, result.sourceLimitExceededCount)
    }

    @Test
    fun `a source with an unrecognized type is skipped without affecting the count`() {
        val result = BackupMergePolicy.merge(
            existingSources = emptyList(),
            existingFavorites = emptyList(),
            importedSources = listOf(backupSource("a").copy(type = "NOT_A_TYPE")),
            importedFavorites = emptyList(),
        )
        assertTrue(result.sources.isEmpty())
        assertEquals(0, result.importedSourceCount)
        assertEquals(0, result.sourceLimitExceededCount)
    }

    @Test
    fun `re-importing the same source id replaces it rather than duplicating`() {
        val result = BackupMergePolicy.merge(
            existingSources = listOf(source("a")),
            existingFavorites = emptyList(),
            importedSources = listOf(backupSource("a").copy(displayName = "Renamed")),
            importedFavorites = emptyList(),
        )
        assertEquals(1, result.sources.size)
        assertEquals("Renamed", result.sources[0].displayName)
    }

    @Test
    fun `a forged source id cannot replace an unrelated saved source`() {
        val existing = source("trusted")
        val importedLocation = "http://different"
        val forged = BackupPlaylistSource(
            id = existing.id,
            type = "URL",
            location = importedLocation,
            displayName = "Different",
            addedAtEpochMillis = 1L,
        )

        val result = BackupMergePolicy.merge(
            existingSources = listOf(existing),
            existingFavorites = emptyList(),
            importedSources = listOf(forged),
            importedFavorites = emptyList(),
        )

        assertEquals(2, result.sources.size)
        assertEquals(existing, result.sources.first())
        assertEquals(Fingerprint.of(importedLocation), result.sources.last().id)
    }

    @Test
    fun `favorite identity is rebuilt from channel fields rather than a forged key`() {
        val imported = BackupFavorite(
            key = "borrowed-key",
            displayName = "News",
            streamUrl = "http://example.test/news",
            tvgId = "real-tvg-id",
            groupTitle = "News",
            addedAtMillis = 1L,
        )

        val result = BackupMergePolicy.merge(
            existingSources = emptyList(),
            existingFavorites = emptyList(),
            importedSources = emptyList(),
            importedFavorites = listOf(imported),
        )

        assertEquals("real-tvg-id", result.favorites.single().key)
    }
}
