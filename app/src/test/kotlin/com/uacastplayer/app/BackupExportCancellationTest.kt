package com.uacastplayer.app

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupPlaylistSource
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.data.favorites.FavoritesRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BackupExportCancellationTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val destination = Uri.parse("content://example.test/cancelled-backup.json")
    private val original = Uri.parse("content://example.test/original-playlist.m3u")

    @Test fun anAlreadyCancelledExportNeverOpensItsOutputDocument() = runTest {
        var opened = 0
        shadowOf(application.contentResolver).registerOutputStreamSupplier(destination) {
            opened++
            ByteArrayOutputStream()
        }
        val controller = BackupController(application, FavoritesRepository(application, backgroundScope), this)
        val cancelled = Job().apply { cancel() }

        val outcome = runCatching {
            controller.writeBackup(destination, BackupData(emptyList(), emptyList(), BackupSettings()), cancelled)
        }

        assertEquals("cancelled export must not open a truncating stream", 0, opened)
        assertTrue(outcome.exceptionOrNull() is CancellationException)
    }

    @Test fun cancellationAfterCapturingTheLastPlaylistStopsBeforeOpeningOutput() = runTest {
        val cancelledDuringCapture = Job()
        var opened = 0
        val cancelledOnce = AtomicBoolean()
        shadowOf(application.contentResolver).registerInputStreamSupplier(original) {
            object : ByteArrayInputStream("#EXTM3U\nhttps://example.test/live\n".toByteArray()) {
                override fun close() {
                    if (cancelledOnce.compareAndSet(false, true)) cancelledDuringCapture.cancel()
                    super.close()
                }
            }
        }
        shadowOf(application.contentResolver).registerOutputStreamSupplier(destination) {
            opened++
            ByteArrayOutputStream()
        }
        val source = BackupPlaylistSource("local", "FILE", original.toString(), "Local", 1)
        val data = BackupData(listOf(source), emptyList(), BackupSettings())
        val controller = BackupController(application, FavoritesRepository(application, backgroundScope), this)

        val outcome = runCatching {
            controller.writeBackup(destination, data, cancelledDuringCapture)
        }

        assertEquals("cancellation during capture must stop before opening output", 0, opened)
        assertTrue(outcome.exceptionOrNull() is CancellationException)
    }
}
