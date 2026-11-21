package com.uacastplayer.app

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.favorites.FavoritesRepository
import com.uacastplayer.data.favorites.FavoritesStorage
import com.uacastplayer.favorites.FavoriteChannel
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupDurabilityTest {
    @Test fun `success is not published before disk writes and storage failure is visible`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val uri = Uri.parse("content://fixture/backup.json")
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) {
            ByteArrayInputStream("""{"version":1,"sources":[],"favorites":[],"settings":{}}""".toByteArray())
        }
        val sourceWrite = CompletableDeferred<Boolean>()
        val favoriteWrite = CompletableDeferred<Unit>()
        val storage = object : FavoritesStorage {
            override suspend fun load() = emptyList<FavoriteChannel>()
            override suspend fun save(favorites: List<FavoriteChannel>) {
                favoriteWrite.await()
                throw IOException("storage full")
            }
        }
        val favorites = FavoritesRepository(storage, backgroundScope)
        val controller = BackupController(app, favorites, this, StandardTestDispatcher(testScheduler))
        val job = controller.importFrom(
            uri, { emptyList() }, { emptyList() }, {}, {}, { sourceWrite.await() },
        )
        runCurrent()
        assertNull(controller.backupImportSummary.value)
        sourceWrite.complete(true)
        runCurrent()
        assertNull(controller.backupImportSummary.value)
        favoriteWrite.complete(Unit)
        job.join()
        assertTrue(controller.backupImportSummary.value!!.persistenceFailed)
    }
}
