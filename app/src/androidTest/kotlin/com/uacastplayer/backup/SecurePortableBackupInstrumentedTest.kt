package com.uacastplayer.backup

import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.app.BackupController
import com.uacastplayer.app.BackupRestoreState
import com.uacastplayer.app.BackupRestoreTarget
import com.uacastplayer.app.BackupRestoreWorkflow
import com.uacastplayer.core.security.BackupCipher
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.data.backup.BackupDocumentReader
import com.uacastplayer.data.favorites.FavoritesRepository
import com.uacastplayer.data.playlist.PlaylistOutcome
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.playlist.PlaylistSource
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Own cache files only. Run with the preserved-data runner to isolate favorites and restored M3U. */
@RunWith(AndroidJUnit4::class)
class SecurePortableBackupInstrumentedTest {
    @Test fun encryptedLocalPlaylistIsRestoredOnlyAfterPreviewConfirmation(): Unit = runBlocking {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        assertEquals("com.uacastplayer.debug", application.packageName)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val favorites = FavoritesRepository(application, scope)
        val controller = BackupController(application, favorites, scope)
        val prefix = "secure-backup-test-${UUID.randomUUID()}"
        val directory = File(application.filesDir, "backup_playlists").also { check(it.mkdirs() || it.isDirectory) }
        // Internal file URLs are only permitted within the owned restored-playlist directory.
        // Real external selections use content:// SAF, not arbitrary file:// paths.
        val local = File(directory, "${Fingerprint.of(prefix)}.m3u")
        val backup = File(application.cacheDir, "$prefix.uacast")
        val password = "Mi TV long test password".toCharArray()
        try {
            favorites.awaitLoaded()
            val original = "#EXTM3U\n#EXTINF:-1 tvg-id=\"news\",Україна España\nhttps://example.test/live\n"
            local.writeBytes(original.toByteArray(Charsets.UTF_16))
            val data = BackupData(
                listOf(BackupPlaylistSource("old", "FILE", Uri.fromFile(local).toString(), "Local", 1)),
                emptyList(), BackupSettings(bufferSize = "SMALL"),
            )
            withContext(Dispatchers.Main) {
                controller.exportCurrentTo(Uri.fromFile(backup), password) { data }
            }
            assertEquals(BackupExportResult.SUCCESS, withTimeout(90_000) {
                controller.backupExportResult.first { it != null }
            })
            val bytes = backup.readBytes()
            assertTrue(BackupCipher.isEncrypted(bytes))
            assertFalse(bytes.toString(Charsets.UTF_8).contains("example.test"))
            assertTrue(local.delete())
            exerciseRestore(application, controller, scope, Uri.fromFile(backup), password)
        } finally {
            password.fill('\u0000')
            scope.cancel()
            local.delete()
            backup.delete()
        }
    }

    private suspend fun exerciseRestore(
        application: Application, controller: BackupController, scope: CoroutineScope, uri: Uri, password: CharArray,
    ) {
        var sources = emptyList<PlaylistSource>()
        var settings = BackupSettings()
        val target = BackupRestoreTarget({ sources }, { emptyList() }, { sources = it }, { settings = it }, { true })
        val workflow = BackupRestoreWorkflow(
            scope,
            read = { withContext(Dispatchers.IO) {
                BackupDocumentReader.read(application, it, currentCoroutineContext().job)
            } },
            prepare = controller::preparePortableData,
            current = { BackupData(emptyList(), emptyList(), settings) },
            apply = { controller.importDecoded(it, target) },
        )
        withContext(Dispatchers.Main) { workflow.open(uri) }
        withTimeout(90_000) { workflow.state.first { it is BackupRestoreState.PasswordRequired } }
        withContext(Dispatchers.Main) { workflow.unlock(password) }
        val preview = withTimeout(90_000) { workflow.state.first { it is BackupRestoreState.Preview } }
            as BackupRestoreState.Preview
        assertEquals(1, preview.summary.localPlaylistCount)
        assertEquals(1, preview.summary.sourceCount)
        assertTrue(sources.isEmpty())
        assertEquals(null, settings.bufferSize)
        withContext(Dispatchers.Main) { workflow.confirm() }
        withTimeout(90_000) { workflow.state.first { it == BackupRestoreState.Idle } }
        assertEquals("SMALL", settings.bufferSize)
        val loaded = PlaylistRepository(application).loadFromFile(Uri.parse(sources.single().location))
            as PlaylistOutcome.Loaded
        assertEquals("Україна España", loaded.groups.flatMap { it.channels }.single().displayName)
    }
}
