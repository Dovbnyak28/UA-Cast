package com.uacastplayer.ui.screenshot

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
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.components.IconTierBanner
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.UaTheme
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
            UaCastTheme(AppTheme.CINEMA) {
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
