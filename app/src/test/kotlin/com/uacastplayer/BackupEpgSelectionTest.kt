package com.uacastplayer

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.backup.BackupExportResult
import com.uacastplayer.data.prefs.AppPreferences
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Public ViewModel export/import, with fresh owners representing the source and target devices. */
@RunWith(RobolectricTestRunner::class)
class BackupEpgSelectionTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val store = ViewModelStore()
    private val uri = Uri.parse("content://fixture/epg-backup.json")
    private val destinationGuide = "https://provider.example.test/guide.xml"

    @After fun clear() { store.clear() }

    private fun owner() = AppViewModel(application).also { store.put("app", it) }

    private fun await(ready: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!ready() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("The backup operation must finish", ready())
    }

    private fun export(explicitlyDisabled: Boolean): ByteArray {
        AppPreferences(application).apply {
            customEpgUrl = null
            hasChosenEpgSource = explicitlyDisabled
        }
        val source = owner()
        val output = ByteArrayOutputStream()
        shadowOf(application.contentResolver).registerOutputStreamSupplier(uri) { output }
        source.exportBackupTo(uri)
        await { source.backupExportResult.value != null }
        assertEquals(BackupExportResult.SUCCESS, source.backupExportResult.value)
        return output.toByteArray().also { store.clear() }
    }

    private fun restore(bytes: ByteArray): AppViewModel {
        AppPreferences(application).apply {
            customEpgUrl = destinationGuide
            hasChosenEpgSource = true
        }
        val target = owner()
        shadowOf(application.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
        target.importBackupFrom(uri)
        await { target.backupImportSummary.value != null }
        assertNotNull(target.backupImportSummary.value)
        assertTrue(target.backupImportSummary.value?.persistenceFailed == false)
        return target
    }

    @Test fun anExplicitlyDisabledGuideSurvivesExportAndRestoreOntoAConfiguredDevice() {
        val target = restore(export(explicitlyDisabled = true))
        assertNull("An explicit off choice must replace the target's existing guide", target.epgState.value.customUrl)
        assertNull(target.preferences.customEpgUrl)
        assertTrue(target.preferences.hasChosenEpgSource)
        val suggestion = "https://playlist.example.test/guide.xml"
        target.epgController.handleEpgAutoDetect(listOf(suggestion))
        assertNull("Playlist detection must not re-enable the disabled guide", target.epgState.value.customUrl)
        assertEquals(suggestion, target.epgState.value.suggestedUrl)
        assertNull(target.preferences.customEpgUrl)
    }

    @Test fun anUnchosenGuideDoesNotEraseTheTargetsExistingSelection() {
        val target = restore(export(explicitlyDisabled = false))
        assertEquals(destinationGuide, target.epgState.value.customUrl)
        assertEquals(destinationGuide, target.preferences.customEpgUrl)
    }

    @Test fun aLegacyBackupWithoutAGuideChoiceKeepsTheTargetsExistingSelection() {
        val bytes = """{"version":1,"sources":[],"favorites":[],"settings":{}}""".toByteArray()
        val target = restore(bytes)
        assertEquals(destinationGuide, target.epgState.value.customUrl)
        assertEquals(destinationGuide, target.preferences.customEpgUrl)
    }
}
