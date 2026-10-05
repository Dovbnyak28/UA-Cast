package com.uacastplayer.app

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.favorites.FavoritesRepository
import com.uacastplayer.data.favorites.FavoritesStorage
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.parentalcontrol.LockedChannelsStorage
import com.uacastplayer.parentalcontrol.ParentalControlPinStorage
import com.uacastplayer.parentalcontrol.PlayerChannelAccess
import com.uacastplayer.playlist.M3uChannel
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Startup ordering regressions: delayed storage must not lose user data or bypass restrictions. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StartupBoundaryRegressionTest {
    @Test fun importMustRetainFavoritesWhoseStartupReadHasNotFinished() = runTest {
        val old = FavoriteChannel("old", "Existing", "http://example.test/old", null, null, 1L)
        val gate = CompletableDeferred<Unit>()
        var durable = listOf(old)
        val storage = object : FavoritesStorage {
            override suspend fun load(): List<FavoriteChannel> {
                val initial = durable
                gate.await()
                return initial
            }
            override suspend fun save(favorites: List<FavoriteChannel>) { durable = favorites }
        }
        val favorites = FavoritesRepository(storage, backgroundScope)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val uri = Uri.parse("content://audit/backup.json")
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) {
            ByteArrayInputStream(
                ("""{"version":1,"sources":[],"favorites":[{"key":"new","displayName":"Imported",""" +
                    """"streamUrl":"http://example.test/new"}],"settings":{}}""").toByteArray(),
            )
        }
        val controller = BackupController(app, favorites, this, StandardTestDispatcher(testScheduler))
        runCurrent()
        val import = controller.importFrom(uri, { emptyList() }, { favorites.favorites.value }, {}, {})
        runCurrent()
        assertFalse("Import must wait for startup before merging", import.isCompleted)
        gate.complete(Unit)
        import.join()
        assertEquals(false, controller.backupImportSummary.value?.persistenceFailed)
        println("AUDIT import durable favorites=${durable.map { it.displayName }}")
        assertTrue("Pre-existing favorite must survive merge and be saved to disk", old in durable)
    }

    @Test fun savedLockedChannelMustNotRestoreBeforeLockStoreIsReady() = runTest {
        val gate = CompletableDeferred<Unit>()
        val channel = M3uChannel("Protected", "http://example.test/protected")
        val storage = object : LockedChannelsStorage {
            override suspend fun load(): Set<String> {
                gate.await()
                return setOf(channel.displayName)
            }
            override suspend fun save(keys: Set<String>) = Unit
        }
        val pins = object : ParentalControlPinStorage {
            override var parentalControlPinHash: String? = "existing-hash"
            override var parentalControlPinSalt: String? = "existing-salt"
        }
        val controller = ParentalControlController(
            storage, pins, backgroundScope, StandardTestDispatcher(testScheduler),
        )
        controller.loadInitial()
        runCurrent()
        assertTrue(controller.isPinSet.value)
        val allowedDuringLoad = PlayerChannelAccess.mayRestoreAfterProcessDeath(
            channel, controller.lockedKeys.value, { it.displayName }, controller.unlockedThisSession.value,
            locksLoaded = controller.isReady.value,
        )
        gate.complete(Unit)
        runCurrent()
        val allowedAfterLoad = PlayerChannelAccess.mayRestoreAfterProcessDeath(
            channel, controller.lockedKeys.value, { it.displayName }, controller.unlockedThisSession.value,
            locksLoaded = controller.isReady.value,
        )
        println("AUDIT restore allowed before=$allowedDuringLoad after=$allowedAfterLoad")
        assertFalse(allowedAfterLoad)
        assertFalse("An uninitialized lock store must not be interpreted as no restrictions", allowedDuringLoad)
    }
}
