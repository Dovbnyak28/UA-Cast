package com.uacastplayer.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceType
import com.uacastplayer.testing.RequiresComposeTestManifest
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
@Config(qualifiers = "en-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PlaylistSourceSafetyTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `active source removal explains consequences and cancellation writes nothing`() {
        var removed = 0
        var dismissed = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PlaylistSourceRemovalDialog("Fixture", true, { removed++ }, { dismissed++ })
            }
        }
        rule.onNodeWithText("Remove active playlist", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(0, removed)
        assertEquals(1, dismissed)
    }

    @Test fun `historical over-capacity sources remain accessible but cannot silently add another`() {
        val sources = List(20) {
            PlaylistSource("$it", PlaylistSourceType.URL, "https://example/$it", "Source $it", it.toLong())
        }
        var selected: PlaylistSource? = null
        var added = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PlaylistSourceSheet(sources, "19", { selected = it }, {}, { added++ }, {})
            }
        }
        rule.onNodeWithText("Add playlist").assertIsDisplayed()
        rule.onNodeWithTag("playlist-source-list").performScrollToNode(hasText("Source 0"))
        rule.onNodeWithText("Source 0").assertIsDisplayed().performClick()
        assertEquals("0", selected?.id)
        rule.onNodeWithText("Add playlist").assertIsNotEnabled()
        assertEquals(0, added)
    }
}
