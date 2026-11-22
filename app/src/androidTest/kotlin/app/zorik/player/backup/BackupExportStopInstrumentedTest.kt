package app.zorik.player.backup

import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.zorik.player.app.BackupController
import app.zorik.player.data.favorites.FavoritesRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Own cache file only. Preserved-data runner isolates the debug app's favorites/preferences. */
@RunWith(AndroidJUnit4::class)
class BackupExportStopInstrumentedTest {
    @Test fun cancelledPreparationDoesNotOpenAFileAndANewExportSucceeds(): Unit = runBlocking {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        app.zorik.player.testsupport.assertIsolatedDebugPackage(application.packageName)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val startup = CompletableDeferred<Boolean>()
        val controller = BackupController(application, FavoritesRepository(application, scope), scope,
            awaitSourcesLoaded = { startup.await() })
        val file = File(application.cacheDir, "cancel-backup-${UUID.randomUUID()}.json")
        val data = BackupData(emptyList(), emptyList(), BackupSettings(epgDisabled = true))
        var captured = 0
        try {
            withContext(Dispatchers.Main) {
                controller.exportCurrentTo(Uri.fromFile(file)) { captured++; data }
                assertTrue(controller.exportInProgress.value)
                controller.cancelExport()
            }
            assertFalse(controller.exportInProgress.value)
            assertNull(controller.backupExportResult.value)
            assertFalse(file.exists())
            startup.complete(true)
            withContext(Dispatchers.Main) { controller.exportCurrentTo(Uri.fromFile(file)) { captured++; data } }
            assertEquals(BackupExportResult.SUCCESS, withTimeout(30_000) {
                controller.backupExportResult.first { it != null }
            })
            assertEquals(1, captured)
            assertEquals(data, BackupCodec.decode(file.readText()))
        } finally {
            scope.cancel()
            startup.complete(true)
            file.delete()
        }
    }
}
