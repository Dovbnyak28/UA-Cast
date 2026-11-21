package com.uacastplayer.ui.tv

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.uacastplayer.R
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoritesSortOrder
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.playlist.groupDisplayKey
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.home.HomeSourceState
import com.uacastplayer.ui.player.TvPlayerControls
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
class TvNavigationTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `D-pad traverses the grid and OK opens the focused channel once`() {
        val channels = List(40) { M3uChannel("Channel $it", "https://example.test/$it.ts") }
        var selected: Pair<List<M3uChannel>, Int>? = null
        setBrowser(PlaylistUiState(channels = channels), emptySet()) { list, index -> selected = list to index }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tv_channel_0").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag("tv_channel_1").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(channels, selected?.first)
        assertEquals(1, selected?.second)
    }

    @Test fun `hidden groups do not leak into the TV channel list`() {
        val hidden = ChannelGroup.Custom("Hidden")
        val visible = M3uChannel("Visible", "https://example.test/visible.ts")
        val blocked = M3uChannel("Hidden", "https://example.test/hidden.ts", groupTitle = "Hidden")
        val playlist = PlaylistUiState(groups = listOf(GroupedChannels(hidden, listOf(blocked)),
            GroupedChannels(ChannelGroup.Ungrouped, listOf(visible))))
        setBrowser(playlist, setOf(groupDisplayKey(hidden))) { _, _ -> }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Hidden").assertDoesNotExist()
        rule.onNodeWithText("Visible").assertExists()
    }

    @Test fun `D-pad player navigation does not reset focus after each interaction`() {
        var previous = 0
        var playPause = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                TvPresentation(true, remember { TvInputRegistry() }) {
                    val focus = remember { FocusRequester() }
                    TvPlayerControls(PlayerUiState(canControlPlayback = true, canGoPrevious = true, canGoNext = true),
                        {}, { playPause++ }, { previous++ }, {}, focus)
                    LaunchedEffect(focus) { focus.requestFocus() }
                }
            }
        }
        rule.onNodeWithText(context.getString(R.string.nav_channels)).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        rule.onNodeWithText(context.getString(R.string.player_previous)).assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionCenter)
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionCenter)
            }
        assertEquals(1, previous)
        assertEquals(1, playPause)
    }

    @Test fun `search keeps input focus and opens the actual filtered channel`() {
        val channels = List(40) { M3uChannel("Channel $it", "https://example.test/$it.ts") }
        var selected: Pair<List<M3uChannel>, Int>? = null
        setBrowser(PlaylistUiState(channels = channels), emptySet()) { list, index -> selected = list to index }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isNotEmpty() }
        val search = rule.onNodeWithTag("tv_search")
        search.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        search.performTextInput("Channel 12")
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv_channel_1").fetchSemanticsNodes().isEmpty() }
        search.assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag("tv_channel_0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf(channels[12]), selected?.first)
        assertEquals(0, selected?.second)
    }

    @Test fun `TV favorites honor the selected sorting instead of stored insertion order`() {
        val favorites = listOf(FavoriteChannel("b", "Bravo", "https://example.test/b.ts", "b", null),
            FavoriteChannel("a", "Alpha", "https://example.test/a.ts", "a", null))
        var selected: Pair<List<M3uChannel>, Int>? = null
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                TvPresentation(true, remember { TvInputRegistry() }) {
                    TvBrowseScreen(BottomDestination.FAVORITES, PlaylistUiState(),
                        HomeSourceState(emptyList(), null, {}, {}, {}, {}), favorites, emptySet(), { null },
                        { list, index -> selected = list to index },
                        favoriteSortOrder = FavoritesSortOrder.ALPHABETICAL)
                }
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tv_channel_0").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf("Alpha", "Bravo"), selected?.first?.map { it.displayName })
        assertEquals(0, selected?.second)
    }

    private fun setBrowser(playlist: PlaylistUiState, hidden: Set<String>, select: (List<M3uChannel>, Int) -> Unit) {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                TvPresentation(true, remember { TvInputRegistry() }) {
                    TvBrowseScreen(BottomDestination.CHANNELS, playlist,
                        HomeSourceState(emptyList(), null, {}, {}, {}, {}), emptyList(), hidden, { null }, select)
                }
            }
        }
    }
}
