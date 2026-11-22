package app.zorik.player.ui.channels

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.zorik.player.playlist.ChannelGroup
import app.zorik.player.playlist.ChannelSearchResult
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class FavoriteSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val result = ChannelSearchResult(
        channel = M3uChannel("News", "https://example.test/news.m3u8"),
        group = ChannelGroup.Custom("News"),
    )

    @Test
    fun notFavoriteAnnouncesAddAction() {
        composeRule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                ChannelSearchResultsList(
                    results = listOf(result),
                    iconRefreshKey = Unit,
                    resolveIcon = { null },
                    isFavorite = { false },
                    onToggleFavorite = {},
                    onChannelClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add to favorites")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun favoriteAnnouncesRemoveAction() {
        composeRule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                ChannelSearchResultsList(
                    results = listOf(result),
                    iconRefreshKey = Unit,
                    resolveIcon = { null },
                    isFavorite = { true },
                    onToggleFavorite = {},
                    onChannelClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Remove from favorites")
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
