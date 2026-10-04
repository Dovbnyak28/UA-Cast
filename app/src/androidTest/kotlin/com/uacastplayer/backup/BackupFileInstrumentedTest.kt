package com.uacastplayer.backup

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.app.BackupController
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.data.favorites.FavoritesRepository
import com.uacastplayer.data.playlist.PlaylistOutcome
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.playlist.PlaylistSourceStore
import com.uacastplayer.playlist.PlaylistSource
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android document streams. Fake, public test files only; no user's playlist or account.
 * Use run-preserved-device-tests.ps1: its debug data isolation is required for these store tests. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class BackupFileInstrumentedTest {
    private val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val favorites = FavoritesRepository(application, scope)
    private val sources = PlaylistSourceStore(application)
    private val controller = BackupController(application, favorites, scope)
    private val documents = mutableListOf<Uri>()
    private var currentSources = emptyList<PlaylistSource>()
    private var importedSettings = BackupSettings()
    private var saved = true

    @Before fun isolateFixture(): Unit = runBlocking {
        withContext(Dispatchers.Main) {
            favorites.awaitLoaded()
            favorites.reorder(emptyList())
            assertTrue(favorites.awaitPersistence())
        }
        assertTrue(sources.save(emptyList()))
    }

    @After fun cleanup() {
        scope.cancel()
        documents.forEach { application.contentResolver.delete(it, null, null) }
    }

    @Test fun exportedFileRestoresLocalPlaylistWithoutOriginalAndSurvivesNewRepositories(): Unit = runBlocking {
        val local = createDocument("m3u", "application/octet-stream")
        val text = "#EXTM3U\n#EXTINF:-1 group-title=\"Новини\",Україна España\nhttps://example.test/live\n"
        // Raw UTF-16 requires the existing CharsetDetector, not a UTF-8-only backup shortcut.
        application.contentResolver.openOutputStream(local, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_16)) }
        val backup = createDocument("json", "application/json")
        application.contentResolver.openOutputStream(backup, "wt")!!.use {
            it.write("old-backup-tail".repeat(2_000).toByteArray())
        }
        val data = BackupData(
            listOf(BackupPlaylistSource("old", "FILE", local.toString(), "Локальні канали", 100)),
            listOf(BackupFavorite("old-key", "Україна España", "https://example.test/live", "ua", "Новини", 101)),
            BackupSettings(bufferSize = "SMALL"),
        )
        withContext(Dispatchers.Main) { controller.exportTo(backup, data) }
        assertEquals(BackupExportResult.SUCCESS, withTimeout(TIMEOUT_MILLIS) {
            controller.backupExportResult.first { it != null }
        })
        val json = application.contentResolver.openInputStream(backup)!!.bufferedReader().use { it.readText() }
        assertTrue(BackupCodec.decode(json)!!.sources.single().playlistBase64 != null)
        assertEquals(1, application.contentResolver.delete(local, null, null))
        documents.remove(local)

        restore(backup)
        assertEquals(1, controller.backupImportSummary.value!!.importedSourceCount)
        assertFalse(controller.backupImportSummary.value!!.persistenceFailed)
        assertEquals("SMALL", importedSettings.bufferSize)
        val recovered = PlaylistSourceStore(application).load().single()
        assertNotEquals(local.toString(), recovered.location)
        assertEquals(Fingerprint.of(recovered.location), recovered.id)
        val loaded = PlaylistRepository(application).loadFromFile(Uri.parse(recovered.location)) as PlaylistOutcome.Loaded
        assertEquals("Україна España", loaded.groups.flatMap { it.channels }.single().displayName)
        val freshFavorites = FavoritesRepository(application, scope)
        freshFavorites.awaitLoaded()
        assertEquals("Україна España", freshFavorites.favorites.value.single().displayName)
        restore(backup)
        assertEquals(1, PlaylistSourceStore(application).load().size)
        assertEquals(1, favorites.favorites.value.size)
    }

    @Test fun damagedPortableFileLeavesSourcesFavoritesAndSettingsUnchanged(): Unit = runBlocking {
        val backup = createDocument("json", "application/json")
        val source = BackupPlaylistSource("x", "FILE", "content://deleted/file", "Broken", 0, "YWJj", "wrong")
        val data = BackupData(listOf(source), emptyList(), BackupSettings(bufferSize = "LARGE"))
        application.contentResolver.openOutputStream(backup, "wt")!!.use {
            it.write(BackupCodec.encode(data).toByteArray())
        }
        importedSettings = BackupSettings(bufferSize = "SMALL")
        restore(backup)
        assertTrue(controller.backupImportSummary.value!!.fileRejected)
        assertEquals("SMALL", importedSettings.bufferSize)
        assertTrue(PlaylistSourceStore(application).load().isEmpty())
        assertTrue(favorites.favorites.value.isEmpty())
    }

    @Test fun oldVersionOneBackupStillImportsOnAndroidJson(): Unit = runBlocking {
        val backup = createDocument("json", "application/json")
        val text = """{"version":1,"sources":[{"id":"old","type":"URL","location":"https://example.test/list.m3u"}],
            "favorites":[],"settings":{"bufferSize":"MEDIUM"}}"""
        application.contentResolver.openOutputStream(backup, "wt")!!.use { it.write(text.toByteArray()) }
        restore(backup)
        assertEquals("https://example.test/list.m3u", PlaylistSourceStore(application).load().single().location)
        assertEquals("MEDIUM", importedSettings.bufferSize)
        assertFalse(controller.backupImportSummary.value!!.fileRejected)
    }

    private suspend fun restore(uri: Uri) = withContext(Dispatchers.Main) {
        withTimeout(TIMEOUT_MILLIS) {
            controller.importFrom(
                uri, { currentSources }, { favorites.favorites.value },
                { currentSources = it; saved = sources.save(it) },
                { importedSettings = it }, { saved },
            ).join()
        }
    }

    private fun createDocument(extension: String, mime: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "ua-cast-backup-test-${UUID.randomUUID()}.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
        }
        return requireNotNull(application.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            .also { documents += it }
    }

    private companion object { const val TIMEOUT_MILLIS = 20_000L }
}
