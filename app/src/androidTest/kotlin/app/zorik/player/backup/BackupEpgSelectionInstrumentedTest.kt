package app.zorik.player.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.zorik.player.AppViewModel
import app.zorik.player.data.prefs.AppPreferences
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
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

/** Public ViewModel path with fresh owners. Requires the preserved-data runner's isolated debug data. */
@RunWith(AndroidJUnit4::class)
class BackupEpgSelectionInstrumentedTest {
    @Test fun anExportedOffChoiceReplacesTheTargetGuideAndPreventsAutomaticReactivation(): Unit = runBlocking {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        app.zorik.player.testsupport.assertIsolatedDebugPackage(application.packageName)
        val store = ViewModelStore()
        val file = File(application.cacheDir, "backup-epg-${UUID.randomUUID()}.json")
        val uri = Uri.fromFile(file)
        try {
            val source = owner(application, store, guide = null)
            withContext(Dispatchers.Main) { source.exportBackupTo(uri) }
            assertEquals(BackupExportResult.SUCCESS, withTimeout(30_000) {
                source.backupExportResult.first { it != null }
            })
            assertTrue(requireNotNull(BackupCodec.decode(file.readText())).settings.epgDisabled)
            withContext(Dispatchers.Main) { store.clear() }
            val target = owner(application, store, guide = "https://target.example.test/guide.xml")
            withContext(Dispatchers.Main) { target.importBackupFrom(uri) }
            val summary = requireNotNull(withTimeout(30_000) {
                target.backupImportSummary.first { it != null }
            })
            assertFalse(summary.persistenceFailed)
            assertNull(target.epgState.value.customUrl)
            assertNull(target.preferences.customEpgUrl)
            assertTrue(target.preferences.hasChosenEpgSource)
            val suggestion = "https://playlist.example.test/guide.xml"
            withContext(Dispatchers.Main) { target.epgController.handleEpgAutoDetect(listOf(suggestion)) }
            assertNull(target.epgState.value.customUrl)
            assertNull(target.preferences.customEpgUrl)
            assertEquals(suggestion, target.epgState.value.suggestedUrl)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            file.delete()
        }
    }

    private suspend fun owner(application: Application, store: ViewModelStore, guide: String?) =
        withContext(Dispatchers.Main) {
            AppPreferences(application).apply {
                customEpgUrl = guide
                hasChosenEpgSource = true
            }
            AppViewModel(application).also { store.put("app", it) }
        }
}
