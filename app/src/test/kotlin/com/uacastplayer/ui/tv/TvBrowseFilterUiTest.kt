package com.uacastplayer.ui.tv

import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.playlist.groupDisplayKey
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w960dp-h540dp-television-xhdpi")
@Category(RequiresComposeTestManifest::class)
class TvBrowseFilterUiTest {
    @get:Rule val rule = createComposeRule()
    private val news = ChannelGroup.Known(ChannelGroup.KEY_NEWS)
    private val sports = ChannelGroup.Known(ChannelGroup.KEY_SPORTS)
    private val first = M3uChannel("News One", "https://unused.test/one")
    private val second = M3uChannel("News Two", "https://unused.test/two")
    private val sport = M3uChannel("Sport", "https://unused.test/sport")
    private val playlist = PlaylistUiState(groups = listOf(
        GroupedChannels(news, listOf(first, second)), GroupedChannels(sports, listOf(sport))))

    @Test fun searchStartsPlaybackAtTheIndexOfTheRenderedFilteredSnapshot() {
        var selected: List<M3uChannel>? = null
        var selectedIndex = -1
        show(emptySet()) { channels, index -> selected = channels; selectedIndex = index }
        awaitText("Sport")
        rule.onNodeWithTag("tv_search").performTextInput("NEWS")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Sport").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("News Two").performClick()
        rule.runOnIdle {
            assertEquals(listOf(first, second), selected)
            assertEquals(1, selectedIndex)
        }
    }

    @Test fun hiddenGroupIsExcludedWithoutChangingVisibleChannelOrder() {
        var selected: List<M3uChannel>? = null
        show(setOf(groupDisplayKey(sports))) { channels, _ -> selected = channels }
        awaitText("News One")
        rule.onNodeWithText("Sport").assertDoesNotExist()
        rule.onNodeWithText("News One").performClick()
        rule.runOnIdle { assertEquals(listOf(first, second), selected) }
    }

    private fun show(hidden: Set<String>, onSelected: (List<M3uChannel>, Int) -> Unit) {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                TvPresentation(true, remember { TvInputRegistry() }) {
                    TvBrowseScreen(BottomDestination.CHANNELS, playlist,
                        HomeSourceState(emptyList(), null, {}, {}, {}, {}), emptyList(), hidden,
                        { null }, onSelected)
                }
            }
        }
    }

    private fun awaitText(text: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
