package app.zorik.player.ui.home

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.zorik.player.epg.EpgUiState
import app.zorik.player.favorites.FavoriteChannel
import app.zorik.player.icons.IconPrefetchUiState
import app.zorik.player.playlist.ChannelGroup
import app.zorik.player.playlist.GroupedChannels
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.playlist.PlaylistUiState
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w320dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class CinemaHomeNavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun primaryChannelShortcutPrecedesTheFavoriteShelfOnSmallPhones() {
        val channel = M3uChannel(displayName = "One", streamUrl = "https://example.test/one")
        rule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                HomeScreen(
                    HomeContentState(
                        PlaylistUiState(groups = listOf(GroupedChannels(ChannelGroup.Custom("Movies"),
                            listOf(channel)))),
                        EpgUiState(nowMillis = 0L), IconPrefetchUiState(),
                        listOf(FavoriteChannel("one", "Favorite One", channel.streamUrl, null, null)), null,
                    ),
                    HomeSourceState(emptyList(), null, {}, {}, {}, {}),
                    resolveIcon = { null }, onChannelSelected = { _, _ -> }, onOpenChannels = {},
                )
            }
        }
        val shortcut = rule.onNodeWithText("View channels").fetchSemanticsNode().boundsInRoot
        val favorite = rule.onNodeWithText("Favorite One").fetchSemanticsNode().boundsInRoot
        assertTrue("Channels must be reachable before the optional favorite shelf", shortcut.bottom <= favorite.top)
    }
}
