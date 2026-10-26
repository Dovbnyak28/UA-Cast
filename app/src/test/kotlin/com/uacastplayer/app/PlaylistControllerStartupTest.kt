package com.uacastplayer.app

import android.app.Application
import java.io.DataOutputStream
import java.io.File
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.playlist.M3uParser
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaylistControllerStartupTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `a stale active id falls back to a source that still exists`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = PlaylistRepository(application, dispatcher)
        val preferences = AppPreferences(application).apply {
            activePlaylistSourceId = "removed-source"
        }
        val remaining = PlaylistSource(
            id = "remaining-source",
            type = PlaylistSourceType.URL,
            location = "https://example.test/remaining.m3u8",
            displayName = "Remaining",
            addedAtEpochMillis = 1L,
        )
        repository.saveSources(listOf(remaining))
        val controller = PlaylistController(
            preferences = preferences,
            playlistRepository = repository,
            scope = this,
            onLoaded = { _, _, _, _ -> },
            onStateChanged = {},
        )

        controller.loadInitialSource()
        advanceUntilIdle()

        assertEquals(remaining.id, controller.activePlaylistSourceId.value)
        assertEquals(remaining.id, preferences.activePlaylistSourceId)
    }

    @Test
    fun `oversized legacy cache is reported and retained instead of being treated as absent`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = PlaylistRepository(application, dispatcher)
        val preferences = AppPreferences(application)
        val previousSources = repository.loadSources()
        val previousActiveId = preferences.activePlaylistSourceId
        val legacyFile = File(application.filesDir, "playlist_snapshot.bin")
        val previousLegacyBytes = if (legacyFile.isFile) legacyFile.readBytes() else null
        val controller = PlaylistController(
            preferences = preferences,
            playlistRepository = repository,
            scope = this,
            onLoaded = { _, _, _, _ -> },
            onStateChanged = {},
        )

        try {
            repository.saveSources(emptyList())
            preferences.activePlaylistSourceId = null
            legacyFile.outputStream().use { stream ->
                DataOutputStream(stream).apply {
                    writeInt(2)
                    writeUTF("legacy-fingerprint")
                    writeBoolean(false)
                    writeLong(1L)
                    writeInt(0)
                    writeInt(M3uParser.MAX_CHANNELS + 1)
                }
            }

            controller.loadInitialSource()
            advanceUntilIdle()

            assertEquals(PlaylistError.ChannelLimitExceeded, controller.playlistState.value.error)
            assertTrue("the only legacy copy must remain available for recovery", legacyFile.isFile)
        } finally {
            repository.saveSources(previousSources)
            preferences.activePlaylistSourceId = previousActiveId
            if (previousLegacyBytes == null) legacyFile.delete() else legacyFile.writeBytes(previousLegacyBytes)
        }
    }
}
