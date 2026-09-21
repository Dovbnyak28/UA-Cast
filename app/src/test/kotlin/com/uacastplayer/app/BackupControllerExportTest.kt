package com.uacastplayer.app

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.backup.BackupCodec
import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupExportResult
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.data.favorites.FavoritesRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupControllerExportTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val uri: Uri = Uri.parse("content://com.example.documents/ua-cast-backup.json")
    private val backup = BackupData(emptyList(), emptyList(), BackupSettings(bufferSize = "SMALL"))

    private fun TestScope.controller() = BackupController(
        application = application,
        favoritesRepository = FavoritesRepository(application, backgroundScope),
        scope = this,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    @Test
    fun `a successful write publishes success and valid backup bytes`() = runTest {
        val written = ByteArrayOutputStream()
        shadowOf(application.contentResolver).registerOutputStream(uri, written)
        val controller = controller()

        controller.exportTo(uri, backup)
        testScheduler.advanceUntilIdle()

        assertEquals(BackupExportResult.SUCCESS, controller.backupExportResult.value)
        val decoded = BackupCodec.decode(written.toString(Charsets.UTF_8.name()))
        assertNotNull(decoded)
        assertEquals("SMALL", decoded?.settings?.bufferSize)
    }

    @Test
    fun `a provider write failure publishes failure`() = runTest {
        shadowOf(application.contentResolver).registerOutputStreamSupplier(uri) {
            object : OutputStream() {
                override fun write(value: Int) = throw IOException("provider disconnected")
            }
        }
        val controller = controller()

        controller.exportTo(uri, backup)
        testScheduler.advanceUntilIdle()

        assertEquals(BackupExportResult.FAILURE, controller.backupExportResult.value)
    }

    @Test
    fun `a crashed provider returning no stream is a failed write`() = runTest {
        shadowOf(application.contentResolver).registerOutputStreamSupplier(uri) { null }

        assertFalse(controller().writeBackup(uri, backup))
    }

    @Test
    fun `a provider runtime failure is converted to export failure`() = runTest {
        shadowOf(application.contentResolver).registerOutputStreamSupplier(uri) {
            throw IllegalArgumentException("provider rejected its own URI")
        }

        assertFalse(controller().writeBackup(uri, backup))
    }

    @Test
    fun `cancellation from the write is never converted to export failure`() = runTest {
        val expected = CancellationException("screen left")
        shadowOf(application.contentResolver).registerOutputStreamSupplier(uri) {
            object : OutputStream() {
                override fun write(value: Int) = throw expected
            }
        }
        val controller = controller()

        val actual = assertThrows(CancellationException::class.java) {
            controller.writeBackup(uri, backup)
        }

        assertSame(expected, actual)
        assertNull(controller.backupExportResult.value)
    }

    @Test
    fun `an older slow export cannot overwrite the newer result`() {
        val slowUri = Uri.parse("content://com.example.documents/slow-backup.json")
        val fastUri = Uri.parse("content://com.example.documents/fast-backup.json")
        val slowStarted = CountDownLatch(1)
        val releaseSlowWrite = CountDownLatch(1)
        shadowOf(application.contentResolver).registerOutputStreamSupplier(slowUri) {
            object : OutputStream() {
                override fun write(value: Int) {
                    slowStarted.countDown()
                    releaseSlowWrite.await(ASYNC_WAIT_SECONDS, TimeUnit.SECONDS)
                    throw IOException("old cloud write failed")
                }
            }
        }
        shadowOf(application.contentResolver).registerOutputStream(fastUri, ByteArrayOutputStream())
        val parentJob = SupervisorJob()
        val scope = CoroutineScope(parentJob + Dispatchers.Default)
        val favoritesScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = BackupController(
            application,
            FavoritesRepository(application, favoritesScope),
            scope,
            Dispatchers.IO,
        )

        try {
            controller.exportTo(slowUri, backup)
            assertTrue("the first export should have entered its provider", slowStarted.await(5, TimeUnit.SECONDS))
            controller.exportTo(fastUri, backup)
            waitUntil { controller.backupExportResult.value == BackupExportResult.SUCCESS }
            releaseSlowWrite.countDown()
            runBlocking { parentJob.children.toList().joinAll() }

            assertEquals(BackupExportResult.SUCCESS, controller.backupExportResult.value)
        } finally {
            releaseSlowWrite.countDown()
            scope.cancel()
            favoritesScope.cancel()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ASYNC_WAIT_SECONDS)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(ASYNC_POLL_MILLIS)
        assertTrue("asynchronous export did not finish in time", condition())
    }

    private companion object {
        const val ASYNC_WAIT_SECONDS = 5L
        const val ASYNC_POLL_MILLIS = 10L
    }
}
