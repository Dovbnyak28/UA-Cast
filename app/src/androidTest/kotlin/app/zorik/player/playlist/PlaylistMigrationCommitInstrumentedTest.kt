package app.zorik.player.playlist

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.zorik.player.app.PlaylistController
import app.zorik.player.app.PlaylistSourceInitialization
import app.zorik.player.core.security.Fingerprint
import app.zorik.player.data.playlist.PlaylistOutcome
import app.zorik.player.data.playlist.PlaylistRepository
import app.zorik.player.data.prefs.AppPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Fixed legacy filename requires the preserved-data runner's isolated debug sandbox. No network requests. */
@RunWith(AndroidJUnit4::class)
class PlaylistMigrationCommitInstrumentedTest {
    @Test fun failedCommitCanBeRetriedAndLaterRemovalSurvivesANewOwner(): Unit = runBlocking {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        app.zorik.player.testsupport.assertIsolatedDebugPackage(application.packageName)
        val repository = PlaylistRepository(application)
        assertTrue(repository.loadSources().isEmpty())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val legacy = File(application.filesDir, "playlist_snapshot.bin")
        val blocked = File(application.filesDir, "playlist_sources.bin.new")
        val marker = File(blocked, "test-owned-marker")
        val url = "https://migration.example.test/legacy.m3u"
        val snapshot = PlaylistSnapshot(Fingerprint.of(url), 42, listOf(M3uChannel("News", "$url/live")), 0, url)
        assertFalse(legacy.exists())
        assertTrue(blocked.mkdir())
        var published = emptyList<PlaylistSource>()
        val initialization = PlaylistSourceInitialization(AppPreferences(application), repository, scope) {
            published = it
        }
        try {
            marker.writeText("block only the source-list commit")
            legacy.outputStream().use { PlaylistSnapshotCodec.encode(snapshot, it) }
            val failure = withContext(Dispatchers.Main) { initialization.awaitLoaded() }
            assertEquals(PlaylistOutcome.StorageError, failure)
            assertTrue(published.isEmpty())
            assertTrue(legacy.isFile)
            assertTrue(repository.loadSources().isEmpty())
            assertTrue(marker.delete())
            assertTrue(blocked.delete())
            assertEquals(null, withContext(Dispatchers.Main) { initialization.awaitLoaded() })
            assertEquals(listOf(snapshot.sourceFingerprint), repository.loadSources().map { it.id })
            assertFalse(legacy.exists())
            removeAndRestart(application, repository, scope, snapshot.sourceFingerprint)
        } finally {
            scope.cancel()
            marker.delete()
            blocked.delete()
            legacy.delete()
        }
    }

    private suspend fun removeAndRestart(
        application: Application, repository: PlaylistRepository, scope: CoroutineScope, id: String,
    ) {
        val controller = withContext(Dispatchers.Main) {
            PlaylistController(AppPreferences(application), repository, scope, { _, _, _, _ -> }, {})
        }
        withContext(Dispatchers.Main) {
            assertEquals(null, controller.sourceInitialization.awaitLoaded())
            controller.removePlaylistSource(id)
            assertTrue(withTimeout(30_000) { controller.awaitSourcesPersistence() })
        }
        scope.cancel()
        val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var restored = emptyList<PlaylistSource>()
        try {
            val restarted = PlaylistSourceInitialization(AppPreferences(application), repository, restartedScope) {
                restored = it
            }
            assertEquals(null, withContext(Dispatchers.Main) { restarted.awaitLoaded() })
            assertTrue(restored.isEmpty())
            assertTrue(repository.loadSources().isEmpty())
        } finally { restartedScope.cancel() }
    }
}
