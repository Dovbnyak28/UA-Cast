package app.zorik.player.ui.channels

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.zorik.player.core.settings.ChannelLayout
import app.zorik.player.core.settings.ListDensity
import app.zorik.player.epg.EpgUiState
import app.zorik.player.playlist.ChannelGroup
import app.zorik.player.playlist.GroupedChannels
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.playlist.groupDisplayKey
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
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
            ZorikTheme(AppTheme.CINEMA) {
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
            ZorikTheme(AppTheme.CINEMA) {
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
            ZorikTheme(AppTheme.CINEMA) {
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
                ZorikTheme(AppTheme.CINEMA) {
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

    /** A result opens the player; the search field must let go. Left focused behind the player it
     * kept the soft keyboard over the video, ate the first Back and, after a rotation, re-opened
     * over a landscape screen until the navigation rail had no height left. */
    @Test fun openingASearchResultReleasesTheSearchField() {
        val match = M3uChannel("First", "https://unused.example.test/one")
        var selected: M3uChannel? = null
        rule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                GroupsOverviewGrid(
                    listOf(GroupedChannels(ChannelGroup.Custom("Visible"), listOf(match))),
                    rememberLazyGridState(), ChannelLayout.LIST, {}, {}, 0, { null }, { false }, {},
                    { selected = it }, emptySet(), emptySet(), {}, {}, {},
                )
            }
        }
        val field = rule.onNode(hasSetTextAction())
        field.performTextInput("Fir")
        field.assertIsFocused()
        awaitText("First")
        rule.onNodeWithText("First").performClick()
        rule.runOnIdle { assertSame(match, selected) }
        rule.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    private fun awaitText(text: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
