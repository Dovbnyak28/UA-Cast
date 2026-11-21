package com.uacastplayer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.uacastplayer.media.LocalVideo
import com.uacastplayer.player.PlayerRequest
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.player.PlayerRequestViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class RestorePlayerRequestTest {
    @get:Rule val rule = createComposeRule()
    private val video = LocalVideo("content://media/video/1", "Movie.mp4", "video/mp4", true)

    @Test fun aDurableFileRestoresWithoutAnyPlaylistOrNetwork() {
        val owner = PlayerRequestViewModel()
        val marker = PlayerRequest.forLocalVideo(video).toSavedRequest()!!
        var restored = 0
        var iptvReads = 0
        rule.setContent {
            RestorePlayerRequest(marker, PlaylistUiState(), true, emptySet(), false, owner, { marker },
                { iptvReads++; "iptv" }, { restored++ }, {})
        }
        rule.waitForIdle()
        assertEquals(video, owner.request.value?.channels?.single()?.localVideo)
        assertEquals(1, restored)
        assertEquals(0, iptvReads)
    }

    @Test fun readinessGateAndAnExistingRequestStillOwnPlayback() {
        val owner = PlayerRequestViewModel()
        val marker = PlayerRequest.forLocalVideo(video).toSavedRequest()!!
        val ready = mutableStateOf(false)
        var restored = 0
        rule.setContent {
            RestorePlayerRequest(marker, PlaylistUiState(), ready.value, emptySet(), false, owner, { marker },
                { null }, { restored++ }, {})
        }
        rule.waitForIdle()
        assertNull(owner.request.value)
        val newer = PlayerRequest.forLocalVideo(video.copy(uri = "content://media/video/2"))
        rule.runOnIdle { owner.open(newer); ready.value = true }
        rule.waitForIdle()
        assertSame(newer, owner.request.value)
        assertEquals(0, restored)
    }

    @Test fun liveChannelRestorationStillUsesTheLatestPersistedKey() {
        val channels = listOf(M3uChannel("A", "https://test/a"), M3uChannel("B", "https://test/b"))
        val marker = SavedPlayerRequest(FavoriteKey.of(channels[0]), 0)
        val owner = PlayerRequestViewModel()
        rule.setContent {
            RestorePlayerRequest(marker, PlaylistUiState(channels = channels), true, emptySet(), false, owner,
                { marker }, { FavoriteKey.of(channels[1]) }, {}, {})
        }
        rule.waitUntil(5000) { owner.request.value != null }
        assertEquals(channels[1], owner.request.value?.channels?.get(owner.request.value!!.startIndex))
    }

    @Test fun aLockedLiveChannelCannotBeRestoredThroughTheNewFilePath() {
        val channel = M3uChannel("Locked", "https://test/locked")
        val key = FavoriteKey.of(channel)
        val marker = SavedPlayerRequest(key, 0)
        val owner = PlayerRequestViewModel()
        var missing = 0
        rule.setContent {
            RestorePlayerRequest(marker, PlaylistUiState(channels = listOf(channel)), true, setOf(key), false, owner,
                { marker }, { key }, {}, { missing++ })
        }
        rule.waitUntil(5000) { missing > 0 }
        assertNull(owner.request.value)
    }
}
