package app.zorik.player.ui.channels

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import app.zorik.player.core.settings.ChannelLayout
import app.zorik.player.core.settings.ListDensity
import app.zorik.player.epg.EpgUiState
import app.zorik.player.playlist.ChannelGroup
import app.zorik.player.playlist.GroupedChannels
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import java.util.concurrent.ConcurrentHashMap
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
class CinemaLargeCatalogTest {
    @get:Rule val rule = createComposeRule()
    @Test fun hundredThousandRowsOnlyResolveIconsForTheVisibleWindows() {
        val channels = List(100_000) { M3uChannel("Channel $it", "https://example.test/$it") }
        val resolved = ConcurrentHashMap.newKeySet<String>()
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            SingleGroupChannelList(GroupedChannels(ChannelGroup.Custom("Large playlist"), channels),
                EpgUiState(nowMillis = 0), 0, { resolved += it.displayName; null },
                ListDensity.FULL, ChannelLayout.LIST, {}, { false }, {}, { false }, {}, {}, {}, onGuide = {})
        } }
        rule.onNodeWithText("Channel 0").assertIsDisplayed()
        rule.runOnIdle { assertTrue("Initial composition must be bounded", resolved.size in 1..40) }
        rule.onNode(hasScrollToIndexAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToIndex(100_000) // One lazy header, then channels 0..99,999.
        rule.onNodeWithText("Channel 99999").assertIsDisplayed()
        rule.runOnIdle { assertTrue("Jumping to the end must not compose intervening rows", resolved.size <= 80) }
    }
}
