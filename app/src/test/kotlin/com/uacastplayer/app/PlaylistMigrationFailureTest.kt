package com.uacastplayer.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.playlist.PlaylistSnapshotStore
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistSnapshot
import com.uacastplayer.playlist.PlaylistSnapshotCodec
import com.uacastplayer.testing.AndroidAtomicRenameShadow
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [AndroidAtomicRenameShadow::class])
class PlaylistMigrationFailureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val snapshot = PlaylistSnapshot(
        "migration-failure", 1L, listOf(M3uChannel("News", "https://example.test/live")), 0, null,
    )

    @Test fun `failed copy retains legacy FILE and can recover on next launch`() = runBlocking {
        val legacy = File(context.filesDir, "playlist_snapshot.bin")
        legacy.outputStream().use { PlaylistSnapshotCodec.encode(snapshot, it) }
        // Make only the new snapshot write fail; sources/preferences remain writable.
        val blocked = File(context.filesDir, "playlist_snapshot_${snapshot.sourceFingerprint}.bin.new")
        assertTrue(blocked.mkdir())
        val marker = File(blocked, "owned-test-marker").apply { writeText("block") }
        assertFalse(PlaylistSnapshotStore(context, snapshot.sourceFingerprint).save(snapshot))
        val repository = PlaylistRepository(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = PlaylistController(AppPreferences(context), repository, scope, { _, _, _, _ -> }, {})
            controller.loadInitialSource()
            val failed = withTimeout(5_000) { controller.playlistState.first { it.error != null } }
            assertEquals(PlaylistError.Storage, failed.error)
            assertTrue(legacy.isFile)
            assertTrue(repository.loadSources().isEmpty())
            assertTrue(marker.delete())
            assertTrue(blocked.delete())
            controller.loadInitialSource()
            val recovered = withTimeout(5_000) { controller.playlistState.first { it.hasChannels } }
            assertEquals("News", recovered.channels.single().displayName)
            assertFalse(legacy.exists())
            assertEquals(1, repository.loadSources().size)
        } finally {
            scope.cancel()
        }
    }

    @Test fun `a failed legacy copy cannot be hidden by saving a new source`() = runBlocking {
        val legacy = File(context.filesDir, "playlist_snapshot.bin")
        legacy.outputStream().use { PlaylistSnapshotCodec.encode(snapshot, it) }
        val blocked = File(context.filesDir, "playlist_snapshot_${snapshot.sourceFingerprint}.bin.new")
        assertTrue(blocked.mkdir())
        val marker = File(blocked, "owned-test-marker").apply { writeText("block") }
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("#EXTM3U\n#EXTINF:-1,New\nhttps://example.test/new.ts\n".toResponseBody()).build()
        }.build()
        val repository = PlaylistRepository(context, Dispatchers.Unconfined, client)
        assertTrue(repository.saveSources(emptyList()))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = PlaylistController(AppPreferences(context), repository, scope, { _, _, _, _ -> }, {})
        val newUrl = "https://example.test/new.m3u8"
        try {
            controller.loadInitialSource()
            withTimeout(5_000) { controller.playlistState.first { it.error == PlaylistError.Storage } }
            controller.loadPlaylistFromUrl(newUrl)
            val refused = withTimeout(5_000) { controller.playlistState.first { !it.isLoading } }
            assertEquals(PlaylistError.Storage, refused.error)
            assertEquals(0, calls.get())
            assertTrue(repository.loadSources().isEmpty())
            assertTrue(legacy.isFile)

            assertTrue(marker.delete())
            assertTrue(blocked.delete())
            controller.loadInitialSource()
            withTimeout(5_000) { controller.playlistState.first { it.hasChannels } }
            controller.loadPlaylistFromUrl(newUrl)
            withTimeout(5_000) { controller.playlistState.first { it.sourceReadyToSave } }
            controller.setPlaylistDisplayName("New")
            val saved = controller.awaitSourcesPersistence()
            assertTrue(
                "save=${controller.playlistState.value.sourceSaveState}, sources=${repository.loadSources().size}",
                saved,
            )
            assertEquals(
                setOf(snapshot.sourceFingerprint, Fingerprint.of(newUrl)),
                repository.loadSources().map { it.id }.toSet(),
            )
            assertFalse(legacy.exists())
        } finally {
            marker.delete()
            blocked.delete()
            scope.cancel()
        }
    }
}
