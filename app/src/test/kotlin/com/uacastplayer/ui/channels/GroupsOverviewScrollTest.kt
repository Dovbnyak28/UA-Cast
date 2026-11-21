package com.uacastplayer.ui.channels

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where the groups overview is scrolled to, across the excursion into a group and back.
 *
 * Reported from use, in these words: "the group list jumps back to the top after coming back from
 * the player, and I have to hunt for the folder I was in again". Opening a group swaps this whole
 * grid out for the single-group list, so a `LazyGridState` remembered *inside* it was discarded the
 * moment a group was opened and started again at the top on the way back - which, on a playlist
 * with a few dozen groups, happens after every channel the user watches.
 *
 * The fix hoisted the state into `ChannelsScreen`, above the switch. What this pins is the half
 * that can silently regress: that [GroupsOverviewGrid] actually *uses* the state it is handed. It
 * grew a `gridState` parameter and one `state = gridState` on the `LazyVerticalGrid`; delete that
 * one argument and the parameter goes quietly unused, the grid falls back to its own remembered
 * state, and the bug is back with nothing in the diff to say so.
 *
 * The scroll is driven through the grid's own semantics rather than by calling `scrollToItem` on
 * the hoisted state, and that is deliberate: a state object that the grid never adopted has no
 * layout to scroll, so that call simply never returns and the regression presents as a suite that
 * hangs. Scrolling the node and then reading the caller's state fails in the ordinary way instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class GroupsOverviewScrollTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Enough groups that the grid genuinely scrolls at this window size. */
    private fun manyGroups(): List<GroupedChannels> = (1..40).map { index ->
        GroupedChannels(
            group = ChannelGroup.Custom("Group $index"),
            channels = listOf(M3uChannel(displayName = "Ch $index", streamUrl = "http://example.test/$index")),
        )
    }

    @Test
    fun theGridScrollsTheStateItWasGiven() {
        lateinit var hoisted: LazyGridState
        composeRule.setContent {
            hoisted = rememberLazyGridState()
            UaCastTheme(AppTheme.CINEMA) {
                Box(Modifier.size(width = 411.dp, height = 700.dp)) { Overview(hoisted) }
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToIndex(TARGET_INDEX)
        composeRule.waitForIdle()

        assertTrue(
            "the grid is scrolling its own state, not the one it was handed",
            hoisted.firstVisibleItemIndex > 0,
        )
    }

    /**
     * The reported journey, end to end: scroll the overview, open a group, come back.
     *
     * The `open` flag stands in for `ChannelsScreen`'s `openGroupKey` and swaps the grid out for
     * something else entirely, exactly as opening a group does. The state lives above that switch,
     * so it is still there afterwards - and the assertion is the position, not merely that the
     * state object survived.
     */
    @Test
    fun theScrollPositionOutlivesOpeningAGroup() {
        lateinit var hoisted: LazyGridState
        var open by mutableStateOf(false)
        composeRule.setContent {
            hoisted = rememberLazyGridState()
            UaCastTheme(AppTheme.CINEMA) {
                Box(Modifier.size(width = 411.dp, height = 700.dp)) {
                    if (open) Text("one group's channels") else Overview(hoisted)
                }
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToIndex(TARGET_INDEX)
        composeRule.waitForIdle()
        val scrolledTo = hoisted.firstVisibleItemIndex
        assertTrue("the fixture never scrolled, so the rest proves nothing", scrolledTo > 0)

        open = true
        composeRule.waitForIdle()
        open = false
        composeRule.waitForIdle()

        assertEquals("the overview came back at the top", scrolledTo, hoisted.firstVisibleItemIndex)
    }

    @Composable
    private fun Overview(gridState: LazyGridState) {
        GroupsOverviewGrid(
            groups = manyGroups(),
            gridState = gridState,
            layout = ChannelLayout.GRID,
            onLayoutChange = {},
            onGroupClick = {},
            iconRefreshKey = 0,
            resolveIcon = { null },
            isFavorite = { false },
            onToggleFavorite = {},
            onChannelClick = {},
            pinnedGroupKeys = emptySet(),
            hiddenGroupKeys = emptySet(),
            onPinGroup = {},
            onHideGroup = {},
            onClearGroupOverride = {},
        )
    }

    private companion object {
        /** Far enough down the 40 groups that the first visible item cannot still be the first. */
        const val TARGET_INDEX = 30
    }
}
