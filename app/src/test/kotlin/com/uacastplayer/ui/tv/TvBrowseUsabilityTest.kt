package com.uacastplayer.ui.tv

import android.content.Context
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.home.HomeSourceState
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w960dp-h540dp-television-xhdpi")
@Category(RequiresComposeTestManifest::class)
class TvBrowseUsabilityTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun emptyFavoritesExplainTheStarInsteadOfAnImportOrSearchFailure() {
        show(BottomDestination.FAVORITES, PlaylistUiState(channels = listOf(channel("News"))))
        rule.onNodeWithText(context.getString(R.string.favorites_empty_message)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.favorites_empty_subtitle)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.player_channels_no_results)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.add_playlist_title)).assertDoesNotExist()
    }

    @Test fun unmatchedSavedFavoriteDoesNotAskToImportAnotherPlaylist() {
        show(BottomDestination.FAVORITES, PlaylistUiState(),
            listOf(FavoriteChannel("news", "News", "https://example.test/news", null, null)))
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tv_search").performTextInput("Unmatched")
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText(context.getString(R.string.player_channels_no_results)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.add_playlist_title)).assertDoesNotExist()
    }

    @Test fun anEmptyChannelBrowserStillOffersImportWhenSavedFavoritesExist() {
        show(BottomDestination.CHANNELS, PlaylistUiState(),
            listOf(FavoriteChannel("news", "News", "https://example.test/news", null, null)))
        rule.onNodeWithText(context.getString(R.string.home_empty_message)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.add_playlist_title)).assertIsDisplayed()
    }

    @Test fun remoteCanClearAnUnmatchedSearchWithoutOpeningAnyChannel() {
        var opens = 0
        show(BottomDestination.CHANNELS, PlaylistUiState(channels = listOf(channel("News")))) { _, _ -> opens++ }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tv_search").performTextInput("Unmatched")
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("tv_search").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        val clear = rule.onNodeWithTag("tv_search_clear_empty")
        clear.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tv_search_clear").assertDoesNotExist()
        rule.onNodeWithTag("tv_search_clear_empty").assertDoesNotExist()
        rule.onNodeWithTag("tv_search").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag("tv_channel_0").assertIsFocused()
        assertEquals(0, opens)
    }

    private fun show(destination: BottomDestination, playlist: PlaylistUiState,
        favorites: List<FavoriteChannel> = emptyList(), select: (List<M3uChannel>, Int) -> Unit = { _, _ -> }) {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                TvPresentation(true, remember { TvInputRegistry() }) {
                    TvBrowseScreen(destination, playlist, HomeSourceState(emptyList(), null, {}, {}, {}, {}),
                        favorites, emptySet(), { null }, select)
                }
            }
        }
    }

    private fun channel(name: String) = M3uChannel(name, "https://example.test/live")
}
