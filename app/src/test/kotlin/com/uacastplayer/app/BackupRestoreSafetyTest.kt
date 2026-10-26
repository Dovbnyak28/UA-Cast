package com.uacastplayer.app

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.backup.BackupCodec
import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupFavorite
import com.uacastplayer.backup.BackupPlaylistSource
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.data.backup.BackupPlaylistFiles
import com.uacastplayer.data.favorites.FavoritesRepository
import com.uacastplayer.data.favorites.FavoritesStorage
import com.uacastplayer.favorites.FavoriteChannel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupRestoreSafetyTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val uri = Uri.parse("content://example.test/backup.json")
    private val live = FavoriteChannel("live", "Live", "https://example.test/live", "live", null, 1)
    private val imported = BackupFavorite("imported", "Imported", "https://example.test/imported", "other", null, 2)
    private fun answer(data: BackupData) {
        shadowOf(application.contentResolver).registerInputStreamSupplier(uri) {
            ByteArrayInputStream(BackupCodec.encode(data).toByteArray())
        }
    }
    private fun storage() = object : FavoritesStorage {
        override suspend fun load() = emptyList<FavoriteChannel>()
        override suspend fun save(favorites: List<FavoriteChannel>) = Unit
    }

    @Test fun `missing original local file fails export before output is opened`() = runTest {
        val local = Uri.parse("content://example.test/missing.m3u")
        shadowOf(application.contentResolver).registerInputStreamSupplier(local) { throw IOException("missing") }
        var outputOpened = false
        shadowOf(application.contentResolver).registerOutputStreamSupplier(uri) {
            outputOpened = true
            ByteArrayOutputStream()
        }
        val repository = FavoritesRepository(storage(), backgroundScope)
        val controller = BackupController(application, repository, this, UnconfinedTestDispatcher(testScheduler))
        val source = BackupPlaylistSource("x", "FILE", local.toString(), "X", 0)
        assertFalse(controller.writeBackup(uri, BackupData(listOf(source), emptyList(), BackupSettings())))
        assertFalse(outputOpened)
    }

    @Test fun `local file storage failure rejects before applying sources or settings`() = runTest {
        val local = Uri.parse("content://example.test/original.m3u")
        shadowOf(application.contentResolver).registerInputStreamSupplier(local) {
            ByteArrayInputStream("#EXTM3U\nhttps://example.test/live\n".toByteArray())
        }
        val source = BackupPlaylistSource("x", "FILE", local.toString(), "X", 0)
        val data = BackupPlaylistFiles(application).capture(
            BackupData(listOf(source), listOf(imported), BackupSettings()), Job(),
        )!!
        answer(data)
        File(application.filesDir, "backup_playlists").writeText("simulated unavailable directory")
        val repository = FavoritesRepository(storage(), backgroundScope)
        repository.awaitLoaded()
        repository.reorder(listOf(live))
        val controller = BackupController(application, repository, this, UnconfinedTestDispatcher(testScheduler))
        var applied = false
        controller.importFrom(uri, { emptyList() }, { repository.favorites.value },
            { applied = true }, { applied = true }).join()
        assertFalse(applied)
        assertEquals(listOf(live), repository.favorites.value)
        assertTrue(controller.backupImportSummary.value!!.fileRejected)
    }

    @Test fun `corrupted embedded payload never changes existing data`() = runTest {
        val source = BackupPlaylistSource("x", "FILE", "content://old/file", "X", 0, "YWJj", "bad digest")
        answer(BackupData(listOf(source), listOf(imported), BackupSettings(bufferSize = "LARGE")))
        val repository = FavoritesRepository(storage(), backgroundScope)
        repository.awaitLoaded()
        repository.reorder(listOf(live))
        val controller = BackupController(application, repository, this, UnconfinedTestDispatcher(testScheduler))
        var applied = false
        controller.importFrom(uri, { emptyList() }, { repository.favorites.value },
            { applied = true }, { applied = true }).join()
        assertFalse(applied)
        assertEquals(listOf(live), repository.favorites.value)
        assertTrue(controller.backupImportSummary.value!!.fileRejected)
    }

    @Test fun `favorite added while source save suspends is not lost`() = runTest {
        answer(BackupData(emptyList(), listOf(imported), BackupSettings()))
        val repository = FavoritesRepository(storage(), backgroundScope)
        val controller = BackupController(application, repository, this, UnconfinedTestDispatcher(testScheduler))
        val sourceSave = CompletableDeferred<Unit>()
        val job = controller.importFrom(uri, { emptyList() }, { repository.favorites.value },
            { sourceSave.await() }, {})
        runCurrent()
        repository.reorder(listOf(live))
        sourceSave.complete(Unit)
        job.join()
        assertEquals(2, repository.favorites.value.size)
        assertTrue(repository.favorites.value.contains(live))
        assertEquals(1, controller.backupImportSummary.value!!.importedFavoriteCount)
    }

    @Test fun `favorite removed while source save suspends is not resurrected`() = runTest {
        answer(BackupData(emptyList(), listOf(imported), BackupSettings()))
        val repository = FavoritesRepository(storage(), backgroundScope)
        repository.awaitLoaded()
        repository.reorder(listOf(live))
        val controller = BackupController(application, repository, this, UnconfinedTestDispatcher(testScheduler))
        val sourceSave = CompletableDeferred<Unit>()
        val job = controller.importFrom(uri, { emptyList() }, { repository.favorites.value },
            { sourceSave.await() }, {})
        runCurrent()
        repository.remove(live.key)
        sourceSave.complete(Unit)
        job.join()
        assertEquals(1, repository.favorites.value.size)
        assertFalse(repository.favorites.value.contains(live))
    }
}
