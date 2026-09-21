package com.uacastplayer.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.playlist.PlaylistSnapshotStore
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistSnapshot
import com.uacastplayer.playlist.PlaylistSnapshotCodec
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

@RunWith(RobolectricTestRunner::class)
class PlaylistMigrationFailureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val snapshot = PlaylistSnapshot(
        "migration-failure", 1L, listOf(M3uChannel("News", "https://example.test/live")), 0, null,
    )

    @Test fun `failed copy retains legacy FILE and can recover on next launch`() = runBlocking {
        val legacy = File(context.filesDir, "playlist_snapshot.bin")
        legacy.outputStream().use { PlaylistSnapshotCodec.encode(snapshot, it) }
        // Make only the new snapshot write fail; sources/preferences remain writable.
        val blocked = File(context.filesDir, "playlist_snapshot_${snapshot.sourceFingerprint}.bin.new")
        assertTrue(blocked.mkdir())
        val marker = File(blocked, "owned-test-marker").apply { writeText("block") }
        assertFalse(PlaylistSnapshotStore(context, snapshot.sourceFingerprint).save(snapshot))
        val repository = PlaylistRepository(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = PlaylistController(AppPreferences(context), repository, scope, { _, _, _, _ -> }, {})
            controller.loadInitialSource()
            val failed = withTimeout(5_000) { controller.playlistState.first { it.error != null } }
            assertEquals(PlaylistError.Storage, failed.error)
            assertTrue(legacy.isFile)
            assertTrue(repository.loadSources().isEmpty())
            assertTrue(marker.delete())
            assertTrue(blocked.delete())
            controller.loadInitialSource()
            val recovered = withTimeout(5_000) { controller.playlistState.first { it.hasChannels } }
            assertEquals("News", recovered.channels.single().displayName)
            assertFalse(legacy.exists())
            assertEquals(1, repository.loadSources().size)
        } finally {
            scope.cancel()
        }
    }
}
