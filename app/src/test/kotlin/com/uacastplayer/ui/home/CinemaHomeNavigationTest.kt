package com.uacastplayer.ui.home

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
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
            UaCastTheme(AppTheme.CINEMA) {
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
