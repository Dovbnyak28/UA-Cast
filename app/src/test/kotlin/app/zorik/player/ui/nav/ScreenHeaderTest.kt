package app.zorik.player.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import app.zorik.player.R
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.UiTestTags
import app.zorik.player.ui.components.ScreenHeader
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class ScreenHeaderTest {
    @get:Rule val rule = createComposeRule()

    @Test fun contextualHeaderHasOneHeadingAndOneBackAction() {
        var back = 0
        rule.setContent { ZorikTheme(AppTheme.DAYLIGHT) {
            Column { ScreenHeader("Вигляд і мова", onBack = { back++ }) }
        } }
        rule.onAllNodesWithTag(UiTestTags.ROOT_TOP_BAR_TITLE).assertCountEquals(1)
        rule.onNodeWithText("Вигляд і мова").assertIsDisplayed()
        val context = ApplicationProvider.getApplicationContext<Context>()
        rule.onNodeWithContentDescription(context.getString(R.string.settings_back_to_overview)).performClick()
        assertEquals(1, back)
    }

    @Test fun longHeadingAtLargeTextDoesNotPushBackOrRemoteOutsideTheScreen() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                ZorikTheme(AppTheme.DAYLIGHT) {
                    // The header carries the remote only during a live session on a phone.
                    CompositionLocalProvider(
                        app.zorik.player.ui.remote.LocalRemoteHeaderState provides
                            app.zorik.player.ui.remote.RemoteHeaderState(connected = true),
                    ) {
                        ScreenHeader("Джерела каналів і телегід", onBack = {})
                    }
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(R.string.settings_back_to_overview, R.string.remote_title).forEach { resource ->
            val node = rule.onNodeWithContentDescription(context.getString(resource)).assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue(bounds.left.value >= 0 && bounds.right.value <= 320)
            assertTrue("Button $resource bounds=$bounds", (bounds.right - bounds.left).value >= 47.5f &&
                (bounds.bottom - bounds.top).value >= 47.5f)
        }
    }
}
