@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.uacastplayer.ui.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import com.uacastplayer.player.SelectableTrack
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
class TrackPickerDialogTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `empty audio is explained rather than a blank dialog`() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) { TrackPickerDialog("Audio", emptyList(), onSelect = {}, onDismiss = {}) }
        }
        rule.onNodeWithText("No selectable tracks in this stream.").assertIsDisplayed()
        rule.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test fun `loading audio is distinguishable from no tracks`() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                TrackPickerDialog("Audio", emptyList(), onSelect = {}, onDismiss = {}, isLoading = true)
            }
        }
        rule.onNodeWithText("Tracks are still loading.", substring = true).assertIsDisplayed()
    }

    @Test fun `last of thirty tracks is scrollable and selects the correct track`() {
        val group = TrackGroup(Format.Builder().setId("fixture").build())
        val tracks = List(30) { SelectableTrack(group, it, "Track $it", it == 0) }
        var chosen: Int? = null
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                TrackPickerDialog("Audio", tracks, onSelect = { chosen = it.indexInGroup }, onDismiss = {})
            }
        }
        rule.onNodeWithText("Track 29").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(29, chosen)
        rule.onNodeWithText("Close").assertIsDisplayed()
    }
}
