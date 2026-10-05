package com.uacastplayer.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.playlist.PlaylistSourcePolicy

class BackupPreviewTest {
    private val empty = BackupData(emptyList(), emptyList(), BackupSettings())

    @Test fun onlyKnownChangedSettingsAreCountedAndCustomGuideWins() {
        val imported = empty.copy(settings = BackupSettings(
            iconDisplayMode = "UNKNOWN", bufferSize = "LARGE", epgSourceId = "unknown",
            epgCustomUrl = "https://private.example.test/guide?password=secret",
        ))
        assertEquals(listOf("bufferSize", "epgCustomUrl"), BackupPreviewPolicy.create(imported, empty).changedSettings)
    }

    @Test fun duplicateFavoritesUseRealChannelIdentityNotUntrustedSerializedKeys() {
        val a = BackupFavorite("untrusted-a", "Channel", "https://test/one", "id", null, 1)
        val b = a.copy(key = "untrusted-b")
        assertEquals(1, BackupPreviewPolicy.create(empty.copy(favorites = listOf(a, b)), empty).favoriteCount)
    }

    @Test fun sameSettingsAndSourcesDoNotLeakLocationsToSummary() {
        val source = BackupPlaylistSource("id", "FILE", "content://private", "Name", 1, "payload", "digest")
        val data = empty.copy(sources = listOf(source), settings = BackupSettings(bufferSize = "LARGE"))
        val preview = BackupPreviewPolicy.create(data, data)
        assertEquals(1, preview.sourceCount)
        assertEquals(1, preview.localPlaylistCount)
        assertEquals(emptyList<String>(), preview.changedSettings)
    }

    @Test fun sourceLimitMatchesActualImportAndDoesNotPromiseSkippedLocalFiles() {
        val existing = (1..PlaylistSourcePolicy.MAX_SOURCES).map {
            val url = "https://example.test/$it"
            BackupPlaylistSource(Fingerprint.of(url), "URL", url, "Source $it", 0)
        }
        val local = BackupPlaylistSource("id", "FILE", "content://new/file", "Local", 0, "payload", "digest")
        val preview = BackupPreviewPolicy.create(empty.copy(sources = listOf(local)), empty.copy(sources = existing))
        assertEquals(0, preview.sourceCount)
        assertEquals(0, preview.localPlaylistCount)
        assertEquals(1, preview.skippedSourceCount)
    }
}
