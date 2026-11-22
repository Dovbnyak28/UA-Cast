package app.zorik.player.ui.channels

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.zorik.player.core.settings.ChannelLayout
import app.zorik.player.core.settings.ListDensity
import app.zorik.player.epg.EpgUiState
import app.zorik.player.playlist.ChannelGroup
import app.zorik.player.playlist.GroupedChannels
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w320dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class CinemaCatalogAccessibilityTest {
    @get:Rule val rule = createComposeRule()
    @Test fun changingLayoutKeepsTheSameChannelInViewInBothDirections() {
        var layout by mutableStateOf(ChannelLayout.LIST)
        val channels = List(100) { M3uChannel("Channel $it", "https://example.test/$it") }
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            SingleGroupChannelList(GroupedChannels(ChannelGroup.Custom("Group"), channels),
                EpgUiState(nowMillis = 0), 0, { null }, ListDensity.FULL, layout,
                { layout = it }, { false }, {}, { false }, {}, {}, {}, onGuide = {})
        } }
        rule.onNode(hasScrollToIndexAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(51)
        rule.onNodeWithText("Channel 50").assertIsDisplayed()
        rule.runOnIdle { layout = ChannelLayout.GRID }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Channel 50").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Channel 50").assertIsDisplayed()
        rule.runOnIdle { layout = ChannelLayout.LIST }
        rule.onNodeWithText("Channel 50").assertIsDisplayed()
    }

    @Test fun qualityMetadataRemainsVisibleInBothListAndGrid() {
        var layout by mutableStateOf(ChannelLayout.LIST)
        val channel = M3uChannel("Cinema HD", "https://example.test/hd")
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            SingleGroupChannelList(GroupedChannels(ChannelGroup.Custom("Group"), listOf(channel)),
                EpgUiState(nowMillis = 0), 0, { null }, ListDensity.FULL, layout,
                { layout = it }, { false }, {}, { false }, {}, {}, {}, onGuide = {})
        } }
        rule.onNodeWithText("HD").assertIsDisplayed()
        rule.runOnIdle { layout = ChannelLayout.GRID }
        rule.onNodeWithText("HD").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-w393dp-h891dp-xhdpi")
    fun adaptiveGridNeverCompressesFavoriteTargetsBelow48dp() {
        val channels = (1..6).map { M3uChannel("Channel $it", "https://example.test/$it") }
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                SingleGroupChannelList(GroupedChannels(ChannelGroup.Custom("Group"), channels),
                    EpgUiState(nowMillis = 0), 0, { null }, ListDensity.FULL, ChannelLayout.GRID,
                    {}, { false }, {}, { false }, {}, {}, {}, onGuide = {})
            }
        } }
        val target = rule.onAllNodesWithContentDescription("Add to favorites")[0]
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Adaptive columns must reserve two 48dp actions plus tile padding",
            target.width / rule.density.density >= 48f)
    }

    @Test fun guideRemainsReachableAt200PercentWhenMiniPlayerReducesTheViewport() {
        val channels = (1..10).map { M3uChannel("Channel $it", "https://example.test/$it") }
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
            ZorikTheme(AppTheme.CINEMA) {
                // The remaining catalog viewport after the app bar, enlarged nav and mini player.
                Box(Modifier.size(320.dp, 240.dp)) {
                    SingleGroupChannelList(GroupedChannels(ChannelGroup.Custom("A very long group title"), channels),
                        EpgUiState(nowMillis = 0), 0, { null }, ListDensity.FULL, ChannelLayout.LIST,
                        {}, { false }, {}, { false }, {}, {}, {}, onGuide = {},
                        categoryStrip = { CinemaCategoryStrip(
                            listOf(GroupedChannels(ChannelGroup.Custom("A very long group title"), channels)),
                            ChannelGroup.Custom("A very long group title"), emptySet(), emptySet(), {}) })
                }
            }
        } }
        rule.onNode(hasScrollToNodeAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasContentDescription("Guide: Channel 10"))
        rule.onNodeWithContentDescription("Guide: Channel 10").assertIsDisplayed()
    }
}
