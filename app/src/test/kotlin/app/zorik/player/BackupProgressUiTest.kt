package app.zorik.player

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.settings.BackupProgressDialog
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class BackupProgressUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun aBlockedExportCanBeCancelledFromTheActualDocumentActions() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = ViewModelStore()
        val viewModel = AppViewModel(application).also { store.put("app", it) }
        val uri = Uri.parse("content://fixture/blocked-backup.json")
        val stream = HeldOutput()
        shadowOf(application.contentResolver).registerOutputStreamSupplier(uri) { stream }
        try {
            rule.setContent { ZorikTheme(AppTheme.CINEMA) { rememberBackupDocumentActions(viewModel) } }
            rule.runOnIdle { viewModel.exportBackupTo(uri) }
            rule.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idle()
                stream.started.count == 0L
            }
            rule.onNodeWithText("Скасувати").assertExists().performClick()
            rule.waitUntil(5_000) { !viewModel.backupController.exportInProgress.value && stream.closed.get() }
            rule.onNodeWithText("Обробка резервної копії…").assertDoesNotExist()
            assertNull(viewModel.backupExportResult.value)
            assertTrue(stream.closed.get())
        } finally {
            stream.close()
            rule.runOnIdle { store.clear() }
        }
    }

    @Test fun confirmedRestorationDoesNotOfferInterruptionHalfwayThroughApplying() {
        rule.setContent { ZorikTheme(AppTheme.CINEMA) { BackupProgressDialog() } }
        rule.onNodeWithText("Обробка резервної копії…").assertExists()
        rule.onNodeWithText("Скасувати").assertDoesNotExist()
    }

    @Test fun aReadingOperationOffersAnExplicitCancelAction() {
        var cancelled = 0
        rule.setContent { ZorikTheme(AppTheme.CINEMA) { BackupProgressDialog(onCancel = { cancelled++ }) } }
        rule.onNodeWithText("Скасувати").performClick()
        org.junit.Assert.assertEquals(1, cancelled)
    }

    private class HeldOutput : OutputStream() {
        val started = CountDownLatch(1)
        val closed = AtomicBoolean()
        private val released = CountDownLatch(1)

        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            started.countDown()
            check(released.await(20, TimeUnit.SECONDS)) { "Provider fixture was not released" }
            throw IOException("Provider fixture closed")
        }

        override fun close() { closed.set(true); released.countDown() }
    }
}
