package com.uacastplayer.playlist

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.MainActivity
import com.uacastplayer.R
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.testsupport.appViewModelOf
import com.uacastplayer.testsupport.skipOnboarding
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The preserved-data runner is required: startup consumes a test-owned fixed legacy filename. */
@RunWith(AndroidJUnit4::class)
class PlaylistMigrationRecoveryUiInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun storageFailureOffersRetryAndRecoversTheLegacyFileWithoutRestarting() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        com.uacastplayer.testsupport.assertIsolatedDebugPackage(app.packageName)
        val repository = PlaylistRepository(app)
        assertTrue(runBlocking { repository.loadSources().isEmpty() })
        val legacy = File(app.filesDir, "playlist_snapshot.bin")
        val blocked = File(app.filesDir, "playlist_sources.bin.new")
        val marker = File(blocked, "test-owned-marker")
        val id = Fingerprint.of("native-ui-migration-file")
        val snapshot = PlaylistSnapshot(id, 42, listOf(M3uChannel("Migration news", "https://test/live")), 0, null)
        assertFalse(legacy.exists())
        assertTrue(blocked.mkdir())
        try {
            marker.writeText("block the source-list commit")
            legacy.outputStream().use { PlaylistSnapshotCodec.encode(snapshot, it) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity { activity = it; skipOnboarding(it) }
                compose.waitUntil(30_000) {
                    appViewModelOf(activity).playlistState.value.error == PlaylistError.Storage
                }
                compose.onNodeWithContentDescription(activity.getString(R.string.nav_channels)).performClick()
                compose.onNodeWithText(activity.getString(R.string.common_retry)).assertIsDisplayed()
                assertTrue(marker.delete())
                assertTrue(blocked.delete())
                compose.onNodeWithText(activity.getString(R.string.common_retry)).performClick()
                compose.waitUntil(30_000) { appViewModelOf(activity).playlistState.value.hasChannels }
                assertEquals(listOf(id), runBlocking { repository.loadSources().map { it.id } })
                assertFalse(legacy.exists())
                compose.onNodeWithText(activity.getString(R.string.playlist_error_storage)).assertDoesNotExist()
                removeOwnedSource(activity, scenario, id)
            }
        } finally { marker.delete(); blocked.delete(); legacy.delete() }
    }

    private fun removeOwnedSource(activity: MainActivity, scenario: ActivityScenario<MainActivity>, id: String) {
        scenario.onActivity { appViewModelOf(it).playlistController.removePlaylistSource(id) }
        runBlocking { withContext(Dispatchers.Main) {
            assertTrue(withTimeout(30_000) { appViewModelOf(activity).playlistController.awaitSourcesPersistence() })
        } }
    }
}
