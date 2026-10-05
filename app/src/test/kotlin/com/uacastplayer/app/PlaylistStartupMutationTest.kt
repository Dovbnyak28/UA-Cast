package com.uacastplayer.app

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.backup.BackupCodec
import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupExportResult
import com.uacastplayer.backup.BackupPlaylistSource
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.data.favorites.FavoritesRepository
import com.uacastplayer.data.favorites.FavoritesStorage
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourcePolicy
import com.uacastplayer.playlist.PlaylistSourceSaveState
import com.uacastplayer.playlist.PlaylistSourceType
import com.uacastplayer.testing.AndroidAtomicRenameShadow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [AndroidAtomicRenameShadow::class])
class PlaylistStartupMutationTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val io = HeldFirstReadDispatcher()
    private val requests = AtomicInteger()
    private val requestedUrls = ConcurrentLinkedQueue<String>()
    private val loadedEvents = AtomicInteger()
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        requests.incrementAndGet()
        requestedUrls.add(chain.request().url.toString())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("#EXTM3U\n#EXTINF:-1,New channel\nhttps://example.test/new.ts\n".toResponseBody()).build()
    }.build()
    private val repository = PlaylistRepository(app, io, client)
    private val preferences = AppPreferences(app)
    private val controller = PlaylistController(
        preferences, repository, scope, { _, _, _, _ -> loadedEvents.incrementAndGet() }, {},
    )

    @After fun close() {
        scope.cancel()
        io.release()
    }

    @Test fun `adding while startup source read is suspended preserves existing sources on disk`() = runBlocking {
        val existing = source(1)
        assertTrue(repository.saveSources(listOf(existing)))
        preferences.activePlaylistSourceId = existing.id
        io.holdNext()
        controller.loadInitialSource()
        assertTrue("the source read must be suspended before it can publish", io.isHeld)

        controller.loadPlaylistFromUrl(source(2).location)
        io.release()
        withTimeout(5_000) { controller.playlistState.first { it.sourceReadyToSave } }
        assertEquals(0, loadedEvents.get())
        controller.setPlaylistDisplayName("Added during startup")
        assertTrue(controller.awaitSourcesPersistence())
        assertEquals(1, loadedEvents.get())

        val saved = PlaylistRepository(app).loadSources()
        assertEquals(setOf(existing.id, source(2).id), saved.map { it.id }.toSet())
        assertEquals(existing, saved.first { it.id == existing.id })
        assertEquals(source(2).id, controller.activePlaylistSourceId.value)
    }

    @Test fun `startup add rejects the new download at capacity and restores the saved source`() = runBlocking {
        val existing = List(PlaylistSourcePolicy.MAX_SOURCES) { source(it) }
        assertTrue(repository.saveSources(existing))
        io.holdNext()
        controller.loadInitialSource()
        assertTrue(io.isHeld)

        controller.loadPlaylistFromUrl(source(99).location)
        io.release()
        withTimeout(5_000) {
            controller.playlistState.first {
                !it.isLoading && (it.sourceReadyToSave || it.sourceSaveState == PlaylistSourceSaveState.LIMIT_REACHED)
            }
        }
        // Rejecting the pending add deliberately resumes the interrupted saved-source startup.
        // With no snapshot, that legitimate request can race the LIMIT_REACHED publication.
        // Wait for its durable-source UI result and distinguish it from the forbidden new URL.
        withTimeout(5_000) {
            controller.playlistState.first {
                !it.isLoading && it.hasChannels && it.sourceUrl == existing.first().location
            }
        }

        assertEquals(PlaylistSourceSaveState.LIMIT_REACHED, controller.playlistState.value.sourceSaveState)
        assertEquals(1, requests.get())
        assertEquals(listOf(existing.first().location), requestedUrls.toList())
        assertFalse(controller.playlistState.value.sourceReadyToSave)
        assertEquals(existing, PlaylistRepository(app).loadSources())
    }

    @Test fun `backup waits for initial sources before merging and reporting durable success`() = runBlocking {
        val existing = source(1)
        assertTrue(repository.saveSources(listOf(existing)))
        val uri = backupUri(source(2))
        val backup = backupController()
        io.holdNext()
        controller.loadInitialSource()
        assertTrue(io.isHeld)

        val importing = import(backup, uri)
        assertFalse("a merge cannot use the initial empty UI list", importing.isCompleted)
        assertNull(backup.backupImportSummary.value)
        assertEquals(listOf(existing), PlaylistRepository(app).loadSources())
        io.release()
        withTimeout(5_000) { importing.join() }

        assertEquals(1, backup.backupImportSummary.value?.importedSourceCount)
        assertEquals(setOf(existing.id, source(2).id), PlaylistRepository(app).loadSources().map { it.id }.toSet())
        assertEquals(controller.playlistSources.value, PlaylistRepository(app).loadSources())
        assertFalse(backup.backupImportSummary.value!!.persistenceFailed)
    }

    @Test fun `cancelling a backup waiter does not cancel the shared startup source read`() = runBlocking {
        val existing = source(1)
        assertTrue(repository.saveSources(listOf(existing)))
        val backup = backupController()
        io.holdNext()
        controller.loadInitialSource()
        val importing = import(backup, backupUri(source(2)))
        importing.cancel()
        io.release()
        withTimeout(5_000) { controller.sourceInitialization.awaitLoaded() }
        importing.join()

        assertEquals(listOf(existing), controller.playlistSources.value)
        assertEquals(listOf(existing), PlaylistRepository(app).loadSources())
        assertNull(backup.backupImportSummary.value)
    }

    @Test fun `an early backup export captures sources after startup instead of an empty list`() = runBlocking {
        val existing = source(1)
        assertTrue(repository.saveSources(listOf(existing)))
        val uri = Uri.parse("content://fixture/startup-export.json")
        val written = ByteArrayOutputStream()
        shadowOf(app.contentResolver).registerOutputStream(uri, written)
        val backup = backupController()
        val captures = AtomicInteger()
        io.holdNext()
        controller.loadInitialSource()
        assertTrue(io.isHeld)

        backup.exportCurrentTo(uri) {
            captures.incrementAndGet()
            BackupData(
                controller.playlistSources.value.map { saved ->
                    BackupPlaylistSource(
                        saved.id, saved.type.name, saved.location, saved.displayName, saved.addedAtEpochMillis,
                    )
                },
                emptyList(),
                BackupSettings(),
            )
        }
        assertEquals(0, captures.get())
        assertEquals(0, written.size())
        assertNull(backup.backupExportResult.value)
        io.release()
        withTimeout(5_000) { backup.backupExportResult.first { it != null } }

        assertEquals(BackupExportResult.SUCCESS, backup.backupExportResult.value)
        assertEquals(1, captures.get())
        val exported = BackupCodec.decode(written.toString(Charsets.UTF_8.name()))
        assertEquals(listOf(existing.id), exported?.sources?.map { it.id })
    }

    @Test fun `superseded startup adds preserve saved sources and only commit the newest source`() = runBlocking {
        val existing = source(1)
        assertTrue(repository.saveSources(listOf(existing)))
        io.holdNext()
        controller.loadInitialSource()
        controller.loadPlaylistFromUrl(source(2).location)
        controller.loadPlaylistFromUrl(source(3).location)
        io.release()
        withTimeout(5_000) { controller.playlistState.first { it.sourceReadyToSave } }
        assertEquals(source(3).location, controller.playlistState.value.sourceUrl)
        controller.setPlaylistDisplayName("Newest")
        assertTrue(controller.awaitSourcesPersistence())

        assertEquals(setOf(existing.id, source(3).id), PlaylistRepository(app).loadSources().map { it.id }.toSet())
        assertEquals(1, requests.get())
    }

    @Test fun `abandoning an add while startup is suspended still restores the saved source list`() = runBlocking {
        val existing = source(1)
        assertTrue(repository.saveSources(listOf(existing)))
        io.holdNext()
        controller.loadInitialSource()
        controller.loadPlaylistFromUrl(source(2).location)
        controller.cancelPendingSourceAdd()
        assertEquals(0, requests.get())
        io.release()
        withTimeout(5_000) { controller.sourceInitialization.awaitLoaded() }
        withTimeout(5_000) {
            controller.playlistState.first { !it.isLoading && it.sourceUrl == existing.location }
        }

        assertEquals(listOf(existing), controller.playlistSources.value)
        assertEquals(listOf(existing), PlaylistRepository(app).loadSources())
        assertFalse(controller.playlistState.value.sourceReadyToSave)
        assertFalse(controller.playlistState.value.isLoading)
        assertEquals(listOf(existing.location), requestedUrls.toList())
    }

    @Test fun `back after an unsaved completed load restores the active playlist`() = runBlocking {
        val existing = source(1)
        controller.applyImportedSources(listOf(existing)).join()
        controller.switchPlaylistSource(existing)
        withTimeout(5_000) {
            controller.playlistState.first { it.hasChannels && !it.isLoading && it.sourceUrl == existing.location }
        }
        val before = controller.playlistState.value
        assertEquals(1, loadedEvents.get())

        controller.loadPlaylistFromUrl(source(2).location)
        withTimeout(5_000) { controller.playlistState.first { it.sourceReadyToSave } }
        assertEquals(1, loadedEvents.get())
        assertEquals(source(2).location, controller.playlistState.value.sourceUrl)
        controller.cancelPendingSourceAdd()

        assertEquals(before.sourceUrl, controller.playlistState.value.sourceUrl)
        assertEquals(before.channels, controller.playlistState.value.channels)
        assertEquals(1, loadedEvents.get())
        assertEquals(existing.id, controller.activePlaylistSourceId.value)
        assertEquals(listOf(existing), controller.playlistSources.value)
        withTimeout(5_000) {
            val snapshot = File(app.filesDir, "playlist_snapshot_${source(2).id}.bin")
            while (snapshot.exists()) delay(5)
        }
    }

    @Test fun `back from first unsaved completed import leaves no phantom playable playlist`() = runBlocking {
        val pending = source(2)
        controller.loadPlaylistFromUrl(pending.location)
        withTimeout(5_000) { controller.playlistState.first { it.sourceReadyToSave } }
        assertEquals(0, loadedEvents.get())
        controller.cancelPendingSourceAdd()

        assertTrue(controller.playlistSources.value.isEmpty())
        assertTrue(controller.playlistState.value.channels.isEmpty())
        assertNull(controller.playlistState.value.sourceUrl)
        assertNull(controller.activePlaylistSourceId.value)
        assertEquals(0, loadedEvents.get())
    }

    private fun backupController(): BackupController {
        val favorites = FavoritesRepository(object : FavoritesStorage {
            override suspend fun load() = emptyList<FavoriteChannel>()
            override suspend fun save(favorites: List<FavoriteChannel>) = Unit
        }, scope)
        return BackupController(app, favorites, scope, Dispatchers.Unconfined) {
            controller.sourceInitialization.awaitLoaded() == null
        }
    }

    private fun import(backup: BackupController, uri: Uri) = backup.importFrom(
        uri,
        { controller.playlistSources.value },
        { emptyList() },
        { controller.applyImportedSources(it).join() },
        {},
        controller::awaitSourcesPersistence,
    )

    private fun backupUri(source: PlaylistSource): Uri {
        val uri = Uri.parse("content://fixture/startup-backup.json")
        val importedSource = BackupPlaylistSource(
            source.id, source.type.name, source.location, source.displayName, 2L,
        )
        val data = BackupData(
            sources = listOf(importedSource),
            favorites = emptyList(),
            settings = BackupSettings(),
        )
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) {
            ByteArrayInputStream(BackupCodec.encode(data).toByteArray())
        }
        return uri
    }

    private fun source(index: Int): PlaylistSource {
        val url = "https://example.test/$index.m3u8"
        return PlaylistSource(Fingerprint.of(url), PlaylistSourceType.URL, url, "Saved $index", index.toLong())
    }

    /** Holds the disk read without blocking a worker or depending on wall-clock sleeps. */
    private class HeldFirstReadDispatcher : CoroutineDispatcher() {
        private val hold = AtomicBoolean()
        private val held = AtomicReference<Pair<CoroutineContext, Runnable>?>()
        val isHeld: Boolean get() = held.get() != null

        fun holdNext() { hold.set(true) }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (hold.compareAndSet(true, false)) {
                check(held.compareAndSet(null, context to block))
            } else {
                Dispatchers.IO.dispatch(context, block)
            }
        }

        fun release() {
            held.getAndSet(null)?.let { (context, block) -> Dispatchers.IO.dispatch(context, block) }
        }
    }
}
