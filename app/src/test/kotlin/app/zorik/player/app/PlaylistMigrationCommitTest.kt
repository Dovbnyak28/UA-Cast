package app.zorik.player.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.zorik.player.data.playlist.PlaylistOutcome
import app.zorik.player.data.playlist.PlaylistRepository
import app.zorik.player.data.prefs.AppPreferences
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.playlist.PlaylistSnapshot
import app.zorik.player.playlist.PlaylistSnapshotCodec
import app.zorik.player.playlist.PlaylistSource
import app.zorik.player.testing.AndroidAtomicRenameShadow
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [AndroidAtomicRenameShadow::class])
class PlaylistMigrationCommitTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val repository = PlaylistRepository(context, Dispatchers.Unconfined)
    private val legacy = File(context.filesDir, "playlist_snapshot.bin")
    private val blocked = File(context.filesDir, "playlist_sources.bin.new")
    private val marker = File(blocked, "test-owned-marker")
    private var published = emptyList<PlaylistSource>()
    private val initialization = PlaylistSourceInitialization(AppPreferences(context), repository, scope) {
        published = it
    }
    private val snapshot = PlaylistSnapshot(
        "legacy-commit", 42L, listOf(M3uChannel("News", "https://example.test/live")), 0,
        "https://example.test/legacy.m3u",
    )

    @Before fun blockOnlyTheSourceListCommit() {
        legacy.outputStream().use { PlaylistSnapshotCodec.encode(snapshot, it) }
        assertTrue(blocked.mkdir())
        marker.writeText("block only the source-list write")
    }

    @After fun close() { scope.cancel(); marker.delete(); blocked.delete() }

    @Test fun aCopiedSnapshotWithoutASourceCommitIsNotSuccessfulInitialization() = runBlocking {
        assertEquals(PlaylistOutcome.StorageError, initialization.awaitLoaded())
        assertTrue("No uncommitted source may be published as initialized", published.isEmpty())
        assertTrue(legacy.isFile)
        assertTrue(repository.loadSources().isEmpty())
        assertTrue(File(context.filesDir, "playlist_snapshot_${snapshot.sourceFingerprint}.bin").isFile)
    }

    @Test fun repairingStorageAllowsTheSameOwnerToRetryAndRetireLegacyOnlyAfterCommit() = runBlocking {
        initialization.awaitLoaded()
        assertTrue("The original must survive a rejected source commit", legacy.isFile)
        assertTrue(marker.delete())
        assertTrue(blocked.delete())
        assertEquals(null, initialization.awaitLoaded())
        assertEquals(listOf(snapshot.sourceFingerprint), repository.loadSources().map { it.id })
        assertEquals(repository.loadSources(), published)
        assertFalse("Committed migration must not resurrect the legacy source later", legacy.exists())
    }

    @Test fun removingTheMigratedSourceAfterRepairDoesNotResurrectItOnRestart() = runBlocking {
        initialization.awaitLoaded()
        assertTrue(marker.delete())
        assertTrue(blocked.delete())
        initialization.awaitLoaded()
        assertTrue(repository.saveSources(emptyList()))
        repository.deleteSnapshot(snapshot.sourceFingerprint)
        val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var restored = emptyList<PlaylistSource>()
        try {
            val restarted = PlaylistSourceInitialization(AppPreferences(context), repository, restartedScope) {
                restored = it
            }
            assertEquals(null, restarted.awaitLoaded())
            assertTrue("A durable removal must not be undone by leftover legacy data", restored.isEmpty())
            assertTrue(repository.loadSources().isEmpty())
        } finally { restartedScope.cancel() }
    }
}
