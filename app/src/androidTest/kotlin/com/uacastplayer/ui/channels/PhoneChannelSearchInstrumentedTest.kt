package com.uacastplayer.ui.channels

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.R
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.core.settings.ListDensity
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.groupDisplayKey
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated browsing fixtures, with no saved playlist, real PIN, purchase or external stream. */
@RunWith(AndroidJUnit4::class)
class PhoneChannelSearchInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun globalSearchFindsHiddenGroupAliasAndClearsBackToTheOverview() {
        val hidden = ChannelGroup.Custom("Hidden")
        val match = M3uChannel("First", "https://unused.example.test/one", tvgName = " Euro   Sport ")
        var selected: M3uChannel? = null
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                GroupsOverviewGrid(
                    listOf(GroupedChannels(hidden, listOf(match)),
                        GroupedChannels(ChannelGroup.Custom("Visible"), emptyList())),
                    rememberLazyGridState(), ChannelLayout.LIST, {}, {}, 0, { null }, { false }, {},
                    { selected = it }, emptySet(), setOf(groupDisplayKey(hidden)), {}, {}, {},
                )
            }
        }
        rule.onNodeWithText("Hidden").assertDoesNotExist()
        rule.onNode(hasSetTextAction()).performTextInput("euro sport")
        awaitText("First")
        rule.onNodeWithText("First").performClick()
        rule.runOnIdle { assertSame(match, selected) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithContentDescription(context.getString(R.string.channels_clear_search)).performClick()
        awaitText("Visible")
        rule.onNodeWithText("First").assertDoesNotExist()
        rule.onNodeWithText("Hidden").assertDoesNotExist()
    }

    @Test fun groupSearchPreservesDuplicateRowsAndDisplayNameOnlyMatching() {
        val alias = M3uChannel("Alpha", "https://unused.example.test/alias", tvgName = "Target")
        val match = M3uChannel("Target One", "https://unused.example.test/match")
        var selected: M3uChannel? = null
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                SingleGroupChannelList(
                    GroupedChannels(ChannelGroup.Custom("Group"), listOf(alias, match, match)),
                    EpgUiState(), 0, { null }, ListDensity.MINIMAL, ChannelLayout.LIST, {}, { false }, {},
                    { false }, {}, { selected = it }, {},
                )
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("TARGET")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Alpha").fetchSemanticsNodes().isEmpty() }
        rule.onAllNodesWithText("Target One").assertCountEquals(2)
        rule.onAllNodesWithText("Target One")[0].performClick()
        rule.runOnIdle { assertSame(match, selected) }
        rule.onNode(hasSetTextAction()).performTextClearance()
        awaitText("Alpha")
        rule.onAllNodesWithText("Target One").assertCountEquals(2)
    }

    private fun awaitText(text: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
