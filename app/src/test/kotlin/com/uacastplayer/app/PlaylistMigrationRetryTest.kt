package com.uacastplayer.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistSnapshot
import com.uacastplayer.playlist.PlaylistSnapshotCodec
import com.uacastplayer.testing.AndroidAtomicRenameShadow
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [AndroidAtomicRenameShadow::class])
class PlaylistMigrationRetryTest {
    @Test fun aLegacyUrlCanRecoverThroughTheUiRefreshAction() = runBlocking {
        assertRecovery("https://example.test/legacy.m3u")
    }

    @Test fun aLegacyFileCanRecoverThroughTheUiRefreshActionWithoutAUrl() = runBlocking {
        assertRecovery(null)
    }

    private suspend fun assertRecovery(url: String?) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacy = File(context.filesDir, "playlist_snapshot.bin")
        val blocked = File(context.filesDir, "playlist_sources.bin.new")
        val marker = File(blocked, "test-owned-marker")
        val snapshot = PlaylistSnapshot("ui-retry", 42, listOf(M3uChannel("News", "https://test/live")), 0, url)
        legacy.outputStream().use { PlaylistSnapshotCodec.encode(snapshot, it) }
        assertTrue(blocked.mkdir())
        marker.writeText("block the source-list commit")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = PlaylistRepository(context, Dispatchers.Unconfined)
        val controller = PlaylistController(AppPreferences(context), repository, scope, { _, _, _, _ -> }, {})
        try {
            controller.loadInitialSource()
            withTimeout(5_000) { controller.playlistState.first { it.error == PlaylistError.Storage } }
            assertTrue(marker.delete())
            assertTrue(blocked.delete())
            controller.refreshPlaylist() // The action used by Home, Channels and TV.
            val recovered = withTimeout(5_000) { controller.playlistState.first { it.hasChannels } }
            assertEquals("News", recovered.channels.single().displayName)
            assertEquals(listOf(snapshot.sourceFingerprint), repository.loadSources().map { it.id })
            assertFalse(legacy.exists())
        } finally { scope.cancel(); marker.delete(); blocked.delete() }
    }
}
