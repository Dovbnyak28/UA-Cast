package com.uacastplayer.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceType
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val preferences = AppPreferences(context)
    private var nextCode = 200
    private var nextBody = "#EXTM3U\n#EXTINF:-1,Channel A\nhttps://x/a.ts\n"
    private var loadedNotifications = 0
    private val repository = PlaylistRepository(context, httpClient = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(nextCode).message("fixture").body(nextBody.toResponseBody()).build()
    }.build())
    private val controller = PlaylistController(
        preferences, repository, scope, { _, _, _, _ -> loadedNotifications++ }, {},
    )

    @After fun close() = scope.cancel()

    private fun source(name: String) = PlaylistSource(
        Fingerprint.of("https://x/$name.m3u"), PlaylistSourceType.URL, "https://x/$name.m3u", name, 0,
    )

    private suspend fun awaitFinished() = withTimeout(5_000) {
        controller.playlistState.first { !it.isLoading && (it.hasChannels || it.error != null) }
    }

    @Test fun `startup with saved source but missing snapshot fetches source`() = runBlocking {
        val source = source("startup")
        repository.saveSources(listOf(source))
        preferences.activePlaylistSourceId = source.id
        controller.loadInitialSource()
        assertTrue(awaitFinished().hasChannels)
        assertEquals(source.id, controller.activePlaylistSourceId.value)
    }

    @Test fun `failed switch cannot show previous channels or refresh previous URL`() = runBlocking {
        val a = source("a")
        val b = source("b")
        controller.applyImportedSources(listOf(a, b)).join()
        controller.switchPlaylistSource(a)
        assertTrue(awaitFinished().hasChannels)
        nextCode = 404
        controller.switchPlaylistSource(b)
        val failed = awaitFinished()
        assertFalse(failed.hasChannels)
        assertEquals("b", failed.displayName)
        assertEquals(b.location, failed.sourceUrl)
        assertEquals(b.id, controller.activePlaylistSourceId.value)
    }

    @Test fun `empty refresh retains both cached channels and controller ownership`() = runBlocking {
        val a = source("empty-refresh")
        controller.applyImportedSources(listOf(a)).join()
        controller.switchPlaylistSource(a)
        assertTrue(awaitFinished().hasChannels)
        nextBody = "#EXTM3U\n"
        controller.refreshPlaylist()
        assertTrue(awaitFinished().hasChannels)
        assertEquals(1, controller.channelCount)
        assertEquals(1, loadedNotifications)
        val restored = repository.restoreSnapshot(a.id) as com.uacastplayer.data.playlist.PlaylistOutcome.Loaded
        assertEquals(1, restored.groups.sumOf { it.channels.size })
    }

    @Test fun `backup import activates a saved source on a fresh install`() = runBlocking {
        val source = source("import")
        controller.applyImportedSources(listOf(source), activateIfNeeded = true).join()
        assertTrue(awaitFinished().hasChannels)
        assertEquals(source.id, controller.activePlaylistSourceId.value)
    }
}
