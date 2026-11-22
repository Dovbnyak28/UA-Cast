package app.zorik.player.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.zorik.player.epg.EpgUiState
import app.zorik.player.playlist.PlaylistUiState
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w320dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class HomePlaylistCardTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sourceIsShownOnceAndGuideDetailsAreExplicitlyExpanded() {
        var configured = 0
        var changed = 0
        rule.setContent { ZorikTheme(AppTheme.DAYLIGHT) {
            HomePlaylistCard(PlaylistUiState(displayName = "My source"), EpgUiState(), 40_000, 3,
                {}, { changed++ }, { configured++ })
        } }
        rule.onAllNodesWithText("My source").assertCountEquals(1)
        rule.onNodeWithText("Guide not configured").assertDoesNotExist()
        rule.onNodeWithContentDescription("Details").performClick()
        rule.onNodeWithText("Guide not configured").assertIsDisplayed()
        rule.onNodeWithText("Configure guide").performClick()
        assertEquals(1, configured)
        rule.onNodeWithContentDescription("Fewer details").performClick()
        rule.onNodeWithText("Configure guide").assertDoesNotExist()
        rule.onNodeWithContentDescription("Change playlist").performClick()
        assertEquals(1, changed)
    }
}
