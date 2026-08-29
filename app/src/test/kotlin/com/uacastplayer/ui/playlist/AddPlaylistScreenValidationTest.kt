package com.uacastplayer.ui.playlist

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class AddPlaylistScreenValidationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveAction_requiresACompleteHttpUrl() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                AddPlaylistScreen(
                    playlistState = PlaylistUiState(),
                    onSetDisplayName = {},
                    onLoadUrl = {},
                    onPickFile = {},
                    onLoadXtream = { _, _, _ -> },
                    onBackClick = {},
                    onPlaylistLoaded = {},
                )
            }
        }

        val save = composeRule.onNodeWithText("Завантажити та зберегти")
        save.assertIsNotEnabled()
        composeRule.onNodeWithText("Статус").assertDoesNotExist()

        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("provider.example/list.m3u")
        save.assertIsNotEnabled()
        composeRule.onNodeWithText("Введіть повну адресу з http:// або https://").assertExists()
    }

    @Test
    fun validUrlEnablesSaveAction() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                AddPlaylistScreen(
                    playlistState = PlaylistUiState(),
                    onSetDisplayName = {},
                    onLoadUrl = {},
                    onPickFile = {},
                    onLoadXtream = { _, _, _ -> },
                    onBackClick = {},
                    onPlaylistLoaded = {},
                )
            }
        }

        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("https://provider.example/list.m3u")
        composeRule.onNodeWithText("Завантажити та зберегти").assertIsEnabled()
    }

    @Test
    fun loadFeedback_isProgressiveRatherThanDuplicatingEmptyFieldGuidance() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                AddPlaylistScreen(
                    playlistState = PlaylistUiState(isLoading = true),
                    onSetDisplayName = {},
                    onLoadUrl = {},
                    onPickFile = {},
                    onLoadXtream = { _, _, _ -> },
                    onBackClick = {},
                    onPlaylistLoaded = {},
                )
            }
        }

        composeRule.onNodeWithText("Статус").assertExists()
        composeRule.onNodeWithText("Завантаження…").assertExists()
    }
}
