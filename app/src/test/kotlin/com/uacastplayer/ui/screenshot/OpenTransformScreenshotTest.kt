package com.uacastplayer.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.components.openTransform
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.UaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the player's opening animation at a fixed point on a frozen clock.
 *
 * The animation is a third of a second long on a real device, which is exactly long enough to see
 * and far too short to screenshot by hand - `adb screencap` takes longer than the whole transition,
 * so every capture lands on the settled state and proves nothing. Stopping Compose's clock is the
 * only way to assert that the surface is genuinely smaller and semi-transparent partway through,
 * rather than snapping to its final state and animating nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class OpenTransformScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openTransform_partwayThrough() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Box(
                    Modifier
                        .size(width = 411.dp, height = 400.dp)
                        .background(UaTheme.palette.void),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .openTransform(key = Unit)
                            .clip(RoundedCornerShape(RadiusCard))
                            .background(UaTheme.palette.accentGradient),
                    )
                }
            }
        }
        // Far enough in that the surface has visibly grown from its start scale, early enough that
        // it has not arrived - a golden of either end would pass with the animation deleted.
        composeRule.mainClock.advanceTimeBy(ONE_THIRD_OF_ENTER_MILLIS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/open_transform_partway.png")
    }

    private companion object {
        const val ONE_THIRD_OF_ENTER_MILLIS = 230L
    }
}
