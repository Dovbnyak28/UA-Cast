package com.uacastplayer.ui.dlna

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.dlna.DlnaDevice
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
class DlnaConnectionUiTest {
    @get:Rule val rule = createComposeRule()
    private val tv = DlnaDevice("Fixture TV", "https://example/control")

    @Test fun `pending connect names target hides duplicate selection and allows cancel`() {
        var cancelled = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                DlnaDeviceSheetContent(
                    DlnaConnectionState(isConnecting = true, connectingDevice = tv), listOf(tv), false,
                    onDeviceSelected = { error("No duplicate connect") },
                    onStopCasting = { cancelled++ }, onVolumeChange = {},
                )
            }
        }
        rule.onNodeWithText("Connecting to Fixture TV…").assertIsDisplayed()
        rule.onNodeWithText("Fixture TV").assertDoesNotExist()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test fun `failed connection exposes retry and new discovery`() {
        var selected: DlnaDevice? = null
        var searches = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                DlnaDeviceSheetContent(
                    DlnaConnectionState(failedDevice = tv), emptyList(), false,
                    onDeviceSelected = { selected = it }, onStopCasting = {}, onVolumeChange = {},
                    onRetryDiscovery = { searches++ },
                )
            }
        }
        rule.onNodeWithText("Could not start playback", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Try again").performScrollTo().performClick()
        assertEquals(tv, selected)
        rule.onNodeWithText("Search again").performScrollTo().performClick()
        assertEquals(1, searches)
    }
}
