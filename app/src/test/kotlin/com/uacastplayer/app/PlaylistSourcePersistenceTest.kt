package com.uacastplayer.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.playlist.PlaylistSnapshotStore
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistSnapshot
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourcePolicy
import com.uacastplayer.playlist.PlaylistSourceSaveState
import com.uacastplayer.playlist.PlaylistSourceType
import com.uacastplayer.testing.AndroidAtomicRenameShadow
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [AndroidAtomicRenameShadow::class])
class PlaylistSourcePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val repository = PlaylistRepository(context, Dispatchers.Unconfined)
    private val controller = PlaylistController(AppPreferences(context), repository, scope, { _, _, _, _ -> }, {})

    @After fun close() { scope.cancel() }

    private fun source(index: Int): PlaylistSource {
        val location = "https://unused.invalid/$index.m3u8"
        return PlaylistSource(
            Fingerprint.of(location), PlaylistSourceType.URL, location, "Source $index", index.toLong(),
        )
    }

    private fun blockWrites(): Pair<File, File> {
        val directory = File(context.filesDir, "playlist_sources.bin.new")
        assertTrue(directory.mkdir())
        return directory to File(directory, "test-owned-marker").apply { writeText("blocked") }
    }

    private fun unblock(blocked: Pair<File, File>) {
        assertTrue(blocked.second.delete())
        assertTrue(blocked.first.delete())
    }

    @Test fun `failed source mutation is visible and retry survives restart`() = runBlocking {
        val blocked = blockWrites()
        controller.applyImportedSources(listOf(source(1))).join()
        assertFalse(controller.awaitSourcesPersistence())
        assertEquals(PlaylistSourceSaveState.FAILED, controller.playlistState.value.sourceSaveState)
        assertTrue(repository.loadSources().isEmpty())
        unblock(blocked)
        controller.sourcePersistence.retry()
        assertTrue(controller.awaitSourcesPersistence())
        assertEquals(PlaylistSourceSaveState.SAVED, controller.playlistState.value.sourceSaveState)
        assertEquals(listOf(source(1)), PlaylistRepository(context).loadSources())
    }

    @Test fun `failed removal retains snapshot until successful source commit`() = runBlocking {
        val removed = source(1)
        controller.applyImportedSources(listOf(removed)).join()
        val snapshot = PlaylistSnapshot(
            removed.id, 1, listOf(M3uChannel("One", "https://unused.invalid/1.ts")), 0, removed.location,
        )
        assertTrue(PlaylistSnapshotStore(context, removed.id).save(snapshot))
        val file = File(context.filesDir, "playlist_snapshot_${removed.id}.bin")
        val blocked = blockWrites()
        controller.removePlaylistSource(removed.id)
        assertFalse(controller.awaitSourcesPersistence())
        assertTrue(file.isFile)
        assertEquals(listOf(removed), repository.loadSources())
        unblock(blocked)
        controller.sourcePersistence.retry()
        assertTrue(controller.awaitSourcesPersistence())
        // Awaiting the committed write also awaits its snapshot cleanup.
        assertFalse(file.exists())
        assertTrue(repository.loadSources().isEmpty())
    }

    @Test fun `capacity refusal happens before attempting network load`() = runBlocking {
        val sources = List(PlaylistSourcePolicy.MAX_SOURCES) { source(it) }
        controller.applyImportedSources(sources).join()
        controller.loadPlaylistFromUrl(source(99).location)
        assertEquals(PlaylistSourceSaveState.LIMIT_REACHED, controller.playlistState.value.sourceSaveState)
        assertFalse(controller.playlistState.value.isLoading)
        assertEquals(sources, repository.loadSources())
    }

    @Test fun `readded source is not cleaned up by an earlier failed deletion`() = runBlocking {
        val restored = source(1)
        controller.applyImportedSources(listOf(restored)).join()
        val snapshot = PlaylistSnapshot(
            restored.id, 1, listOf(M3uChannel("One", "https://unused.invalid/1.ts")), 0, restored.location,
        )
        assertTrue(PlaylistSnapshotStore(context, restored.id).save(snapshot))
        val blocked = blockWrites()
        controller.removePlaylistSource(restored.id)
        assertFalse(controller.awaitSourcesPersistence())
        unblock(blocked)
        controller.applyImportedSources(listOf(restored)).join()
        assertTrue(File(context.filesDir, "playlist_snapshot_${restored.id}.bin").isFile)
        assertEquals(listOf(restored), repository.loadSources())
    }

    @Test fun `ordinary URL add reports failed commit and retry restores source after restart`() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("#EXTM3U\n#EXTINF:-1,One\nhttps://unused.invalid/1.ts\n".toResponseBody()).build()
        }.build()
        val loader = PlaylistRepository(context, Dispatchers.Unconfined, client)
        val adding = PlaylistController(AppPreferences(context), loader, scope, { _, _, _, _ -> }, {})
        val blocked = blockWrites()
        adding.loadPlaylistFromUrl(source(1).location)
        withTimeout(5_000) { adding.playlistState.first { !it.isLoading } }
        assertEquals(1, adding.playlistState.value.channels.size)
        assertTrue(adding.playlistState.value.sourceReadyToSave)
        adding.setPlaylistDisplayName("My source")
        assertFalse(adding.awaitSourcesPersistence())
        assertEquals(PlaylistSourceSaveState.FAILED, adding.playlistState.value.sourceSaveState)
        assertTrue(repository.loadSources().isEmpty())
        unblock(blocked)
        adding.setPlaylistDisplayName("My source")
        assertTrue(adding.awaitSourcesPersistence())
        assertEquals("My source", PlaylistRepository(context).loadSources().single().displayName)
    }
}
