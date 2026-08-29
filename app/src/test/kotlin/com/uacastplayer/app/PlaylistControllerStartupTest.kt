package com.uacastplayer.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
