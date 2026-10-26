package com.uacastplayer.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.channels.NoSearchResults
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class ChannelRecoveryScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun noResultsOffersClearRecovery() = capture("channels_no_results", largeText = false)
    @Test fun shortViewportAtLargeTextKeepsRecoveryScrollable() = capture("channels_no_results_large", largeText = true)

    private fun capture(name: String, largeText: Boolean) {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, if (largeText) 2f else 1f),
            ) {
                UaCastTheme(AppTheme.CINEMA) {
                    Box(Modifier.size(320.dp, 280.dp).background(UaTheme.palette.void)) {
                        NoSearchResults("Sport", onClearSearch = {})
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
