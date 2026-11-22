package app.zorik.player.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.components.IconTierBanner
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import app.zorik.player.ui.theme.UaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Pins the weak-device notice at the viewport where its former two-row layout hid the grid. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w891dp-h411dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class IconTierBannerScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun iconTierBanner_staysCompactInLandscape() {
        composeRule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(UaTheme.palette.void)
                        .padding(20.dp),
                ) {
                    IconTierBanner(onEnableIcons = {}, onDismiss = {})
                }
            }
        }

        composeRule.onNodeWithText("Логотипи каналів вимкнено для економії ресурсів на цьому пристрої")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Увімкнути іконки").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/icon_tier_banner_landscape.png")
    }
}
