package com.uacastplayer.ui.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistSourceSaveState
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PlaylistSaveUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `download success cannot dismiss until save succeeds and failure can retry`() {
        var state by mutableStateOf(PlaylistUiState(isLoading = true))
        var submitted = 0
        var closed = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                AddPlaylistScreen(state, {
                    submitted++
                    state = state.copy(sourceSaveState = PlaylistSourceSaveState.SAVING)
                }, {}, {}, { _, _, _ -> }, {}, { closed++ })
            }
        }
        rule.waitForIdle()
        rule.runOnIdle {
            state = state.copy(
                isLoading = false, sourceReadyToSave = true,
                channels = listOf(M3uChannel("One", "https://example.test/1")),
            )
        }
        rule.runOnIdle { assertEquals(1, submitted); assertEquals(0, closed) }
        rule.runOnIdle { state = state.copy(sourceSaveState = PlaylistSourceSaveState.FAILED) }
        rule.onNodeWithText("Changes were not saved.", substring = true).assertExists()
        rule.onNodeWithText("Retry saving").performClick()
        rule.runOnIdle { assertEquals(2, submitted); assertEquals(0, closed) }
        rule.runOnIdle { state = state.copy(sourceSaveState = PlaylistSourceSaveState.SAVED) }
        rule.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun `capacity refusal remains visible and is not a success event`() {
        var closed = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                AddPlaylistScreen(
                    PlaylistUiState(sourceSaveState = PlaylistSourceSaveState.LIMIT_REACHED),
                    {}, {}, {}, { _, _, _ -> }, {}, { closed++ },
                )
            }
        }
        rule.onNodeWithText("You can save up to 10 playlists.", substring = true).assertExists()
        rule.runOnIdle { assertEquals(0, closed) }
    }

    @Test fun `fast import is saved even if composition never observed loading`() {
        var submitted = 0
        var closed = 0
        var state by mutableStateOf(PlaylistUiState(sourceReadyToSave = true))
        rule.setContent {
            PlaylistAddCompletion(state, {
                submitted++
                state = state.copy(sourceReadyToSave = false, sourceSaveState = PlaylistSourceSaveState.SAVING)
            }, { closed++ })
        }
        rule.runOnIdle { assertEquals(1, submitted); assertEquals(0, closed) }
        rule.runOnIdle { state = state.copy(sourceSaveState = PlaylistSourceSaveState.SAVED) }
        rule.runOnIdle { assertEquals(1, closed) }
    }
}
