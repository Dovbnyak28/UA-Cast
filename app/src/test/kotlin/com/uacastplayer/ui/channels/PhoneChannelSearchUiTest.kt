package com.uacastplayer.ui.channels

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.core.settings.ListDensity
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.groupDisplayKey
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class PhoneChannelSearchUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun globalSearchKeepsHiddenGroupsTvgNameWhitespaceAndClearBehavior() {
        val hidden = ChannelGroup.Custom("Hidden")
        val visible = ChannelGroup.Custom("Visible")
        val match = M3uChannel("First", "https://unused.example.test/one", tvgName = " Euro   Sport ")
        var selected: M3uChannel? = null
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                GroupsOverviewGrid(
                    listOf(GroupedChannels(hidden, listOf(match)), GroupedChannels(visible, emptyList())),
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
        rule.onNodeWithContentDescription("Clear channel search").performClick()
        awaitText("Visible")
        rule.onNodeWithText("First").assertDoesNotExist()
        rule.onNodeWithText("Hidden").assertDoesNotExist()
    }

    @Test fun groupSearchKeepsDisplayNameOnlyDuplicatesAndTheClickedChannel() {
        val aliasOnly = M3uChannel("Alpha", "https://unused.example.test/alias", tvgName = "Target")
        val match = M3uChannel("Target One", "https://unused.example.test/match")
        var selected: M3uChannel? = null
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                SingleGroupChannelList(
                    GroupedChannels(ChannelGroup.Custom("Group"), listOf(aliasOnly, match, match)),
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
        rule.onNodeWithContentDescription("Clear channel search").performClick()
        awaitText("Alpha")
        rule.onAllNodesWithText("Target One").assertCountEquals(2)
    }

    @Test fun groupNoResultsHasAnExplicitRecoveryAction() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                SingleGroupChannelList(
                    GroupedChannels(ChannelGroup.Custom("Group"),
                        listOf(M3uChannel("Alpha", "https://unused.example.test"))),
                    EpgUiState(), 0, { null }, ListDensity.MINIMAL, ChannelLayout.LIST, {}, { false }, {},
                    { false }, {}, {}, {},
                )
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("No match")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Alpha").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Clear channel search").performClick()
        awaitText("Alpha")
        rule.onNodeWithContentDescription("Clear channel search").assertDoesNotExist()
    }

    @Test fun narrowLargeTextHeaderKeepsTheLayoutActionReachable() {
        var chosen = ChannelLayout.LIST
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
            ) {
                UaCastTheme(AppTheme.CINEMA) {
                    Box(Modifier.size(320.dp, 600.dp)) {
                        GroupsOverviewGrid(
                            listOf(GroupedChannels(ChannelGroup.Custom("Group"), emptyList())),
                            rememberLazyGridState(), ChannelLayout.LIST, { chosen = it }, {},
                            0, { null }, { false }, {},
                            {}, emptySet(), emptySet(), {}, {}, {},
                        )
                    }
                }
            }
        }
        rule.onNodeWithContentDescription("Layout").assertIsDisplayed().performClick()
        rule.onNodeWithText("Grid").performClick()
        rule.runOnIdle { org.junit.Assert.assertEquals(ChannelLayout.GRID, chosen) }
    }

    private fun awaitText(text: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
