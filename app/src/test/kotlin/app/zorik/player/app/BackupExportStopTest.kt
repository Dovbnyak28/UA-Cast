package app.zorik.player.app

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.zorik.player.backup.BackupCodec
import app.zorik.player.backup.BackupData
import app.zorik.player.backup.BackupExportResult
import app.zorik.player.backup.BackupSettings
import app.zorik.player.data.favorites.FavoritesRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BackupExportStopTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val uri = Uri.parse("content://fixture/cancel-backup.json")
    private val data = BackupData(emptyList(), emptyList(), BackupSettings(bufferSize = "SMALL"))

    @Test fun cancellingStartupWaitNeverCapturesOrOpensADocument() = runBlocking {
        val startup = CompletableDeferred<Boolean>()
        var captured = 0
        var opened = 0
        shadowOf(application.contentResolver).registerOutputStreamSupplier(uri) { opened++; ByteArrayOutputStream() }
        Harness { startup.await() }.use { harness ->
            val controller = harness.controller
            controller.exportCurrentTo(uri) { captured++; data }
            assertTrue(controller.exportInProgress.value)
            controller.cancelExport()
            startup.complete(true)
            harness.awaitExports()
            assertFalse(controller.exportInProgress.value)
            assertNull(controller.backupExportResult.value)
            assertEquals(0, captured)
            assertEquals(0, opened)
        }
    }

    @Test fun cancellingAProviderWriteClosesItsStreamAndAllowsRetryAtTheSameDestination() = runBlocking {
        val stream = HeldOutput()
        val output = ByteArrayOutputStream()
        var opened = 0
        shadowOf(application.contentResolver).registerOutputStreamSupplier(uri) {
            if (opened++ == 0) stream else output
        }
        Harness().use { harness ->
            try {
                val controller = harness.controller
                controller.exportTo(uri, data)
                assertTrue(stream.started.await(5, TimeUnit.SECONDS))
                controller.cancelExport()
                assertFalse(controller.exportInProgress.value)
                harness.awaitExports()
                assertTrue(stream.closed.get())
                assertNull(controller.backupExportResult.value)
                controller.exportTo(uri, data)
                assertEquals(BackupExportResult.SUCCESS, withTimeout(5_000) {
                    controller.backupExportResult.first { it != null }
                })
                harness.awaitExports()
                assertEquals(data, BackupCodec.decode(output.toString(Charsets.UTF_8.name())))
                controller.cancelExport() // An already completed export keeps its success notice.
                assertEquals(BackupExportResult.SUCCESS, controller.backupExportResult.value)
            } finally { stream.release() }
        }
    }

    @Test fun lateCompletionOfACancelledExportCannotHideOrDetachTheNewExport() = runBlocking {
        val startup = CompletableDeferred<Boolean>()
        val stream = HeldOutput(releaseOnClose = false)
        shadowOf(application.contentResolver).registerOutputStreamSupplier(uri) { stream }
        Harness { startup.await() }.use { harness ->
            try {
                val controller = harness.controller
                controller.exportTo(uri, data)
                assertTrue(stream.started.await(5, TimeUnit.SECONDS))
                val old = harness.job.children.single()
                controller.cancelExport()
                controller.exportCurrentTo(uri) { data }
                stream.release()
                withTimeout(5_000) { old.join() }
                assertTrue("The newer request still owns the progress dialog", controller.exportInProgress.value)
                assertNull(controller.backupExportResult.value)
                controller.cancelExport()
                assertFalse(controller.exportInProgress.value)
                startup.complete(true)
                harness.awaitExports()
                assertNull(controller.backupExportResult.value)
            } finally { stream.release(); startup.complete(true) }
        }
    }

    private inner class Harness(startup: suspend () -> Boolean = { true }) : AutoCloseable {
        val job = SupervisorJob()
        private val scope = CoroutineScope(job + Dispatchers.Unconfined)
        private val favoritesJob = SupervisorJob()
        private val favoritesScope = CoroutineScope(favoritesJob + Dispatchers.Default)
        val controller = BackupController(
            application, FavoritesRepository(application, favoritesScope), scope, Dispatchers.IO, startup,
        )

        suspend fun awaitExports() = withTimeout(5_000) { job.children.toList().joinAll() }

        override fun close() {
            scope.cancel()
            favoritesScope.cancel()
            runBlocking { withTimeout(5_000) { job.join(); favoritesJob.join() } }
        }
    }

    private class HeldOutput(private val releaseOnClose: Boolean = true) : OutputStream() {
        val started = CountDownLatch(1)
        val closed = AtomicBoolean()
        private val released = CountDownLatch(1)

        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            started.countDown()
            check(released.await(10, TimeUnit.SECONDS)) { "Provider fixture was not released" }
            throw IOException("Closed provider fixture")
        }

        fun release() { released.countDown() }
        override fun close() { closed.set(true); if (releaseOnClose) release() }
    }
}
