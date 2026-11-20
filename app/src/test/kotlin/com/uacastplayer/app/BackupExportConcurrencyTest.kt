package com.uacastplayer.app

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.backup.BackupCodec
import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupExportResult
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.data.favorites.FavoritesRepository
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BackupExportConcurrencyTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val uri = Uri.parse("content://example.test/replaced-backup.json")
    private val older = BackupData(emptyList(), emptyList(), BackupSettings(
        bufferSize = "SMALL", epgCustomUrl = "https://epg.test/" + "a".repeat(500),
    ))
    private val newer = BackupData(emptyList(), emptyList(), BackupSettings(bufferSize = "LARGE"))

    @Test fun aSlowOldExportCannotOverwriteTheNewestBackupAtTheSameUri() {
        HeldDocument().use { document ->
            ExportHarness().use { harness ->
                val controller = harness.controller()
                controller.exportTo(uri, older)
                assertTrue(document.firstStarted.await(5, TimeUnit.SECONDS))
                controller.exportTo(uri, newer)
                document.secondClosed.await(500, TimeUnit.MILLISECONDS)
                document.releaseFirst.countDown()
                harness.joinExports()

                assertEquals(newer, document.decoded())
                assertEquals(1, document.maxActive.get())
                assertEquals(BackupExportResult.SUCCESS, controller.backupExportResult.value)
            }
        }
    }

    @Test fun anObsoleteQueuedExportNeverTruncatesTheSelectedDocument() {
        HeldDocument().use { document ->
            ExportHarness().use { harness ->
                val controller = harness.controller()
                controller.exportTo(uri, older)
                assertTrue(document.firstStarted.await(5, TimeUnit.SECONDS))
                controller.exportTo(uri, older.copy(settings = BackupSettings(bufferSize = "MEDIUM")))
                controller.exportTo(uri, newer)
                document.secondClosed.await(500, TimeUnit.MILLISECONDS)
                document.releaseFirst.countDown()
                harness.joinExports()

                assertEquals("only the active and newest requests may open the document", 2, document.opens.get())
                assertEquals(newer, document.decoded())
                assertEquals(BackupExportResult.SUCCESS, controller.backupExportResult.value)
            }
        }
    }

    @Test fun differentControllerOwnersMustNotWriteTheSameDocumentConcurrently() {
        HeldDocument().use { document ->
            ExportHarness().use { harness ->
                val first = harness.controller()
                val second = harness.controller()
                first.exportTo(uri, older)
                assertTrue(document.firstStarted.await(5, TimeUnit.SECONDS))
                second.exportTo(uri, newer)
                document.secondClosed.await(500, TimeUnit.MILLISECONDS)
                document.releaseFirst.countDown()
                harness.joinExports()

                assertEquals(newer, document.decoded())
                assertEquals(1, document.maxActive.get())
                assertEquals(BackupExportResult.SUCCESS, second.backupExportResult.value)
            }
        }
    }

    private inner class ExportHarness : AutoCloseable {
        private val job = SupervisorJob()
        // Public launch executes up to its IO hop before the next request is submitted, as with
        // Main.immediate. The actual file writers use real IO threads, not a virtual dispatcher.
        private val scope = CoroutineScope(job + Dispatchers.Unconfined)
        private val favoritesScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val favorites = FavoritesRepository(application, favoritesScope)

        fun controller() = BackupController(application, favorites, scope, Dispatchers.IO)
        fun joinExports() = runBlocking { withTimeout(5_000) { job.children.toList().joinAll() } }

        override fun close() {
            scope.cancel()
            favoritesScope.cancel()
            runBlocking { withTimeout(5_000) { job.join() } }
        }
    }

    /** Real independently positioned, truncating file handles, as a SAF file provider returns. */
    private inner class HeldDocument : AutoCloseable {
        private val file = File.createTempFile("backup-race-", ".json", application.cacheDir)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondClosed = CountDownLatch(1)
        val opens = AtomicInteger()
        val maxActive = AtomicInteger()
        private val active = AtomicInteger()

        init {
            shadowOf(application.contentResolver).registerOutputStreamSupplier(uri) { open() }
        }

        private fun open(): OutputStream {
            val handle = opens.incrementAndGet()
            val output = FileOutputStream(file, false)
            maxActive.accumulateAndGet(active.incrementAndGet(), ::maxOf)
            return object : OutputStream() {
                private val closed = AtomicBoolean()

                override fun write(value: Int) = output.write(value)

                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    if (handle == 1) {
                        output.write(bytes, offset, 8)
                        firstStarted.countDown()
                        check(releaseFirst.await(5, TimeUnit.SECONDS))
                        output.write(bytes, offset + 8, length - 8)
                    } else {
                        output.write(bytes, offset, length)
                    }
                }

                override fun close() {
                    if (closed.compareAndSet(false, true)) {
                        if (handle == 1) releaseFirst.countDown()
                        try {
                            output.close()
                        } finally {
                            active.decrementAndGet()
                            if (handle == 2) secondClosed.countDown()
                        }
                    }
                }
            }
        }

        fun decoded(): BackupData? = BackupCodec.decode(file.readText())

        override fun close() {
            releaseFirst.countDown()
            file.delete()
        }
    }
}
