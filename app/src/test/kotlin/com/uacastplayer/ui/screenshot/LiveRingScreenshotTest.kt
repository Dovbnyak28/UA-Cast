package com.uacastplayer.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.components.CastPeerIconGlyphSize
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.components.liveRing
import com.uacastplayer.ui.theme.AppIcons
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

/**
 * The two states of the Cast/DLNA button side by side, with the ring's clock stopped partway
 * through a pulse.
 *
 * Verifying this by hand means starting a real cast session, which means switching on somebody's
 * television - and even then the ring is a slow fade that a screenshot catches at an arbitrary
 * point. A frozen clock gives the one thing that actually matters and is otherwise unobservable:
 * that the idle button draws nothing extra at all, and the live one draws a ring outside its own
 * edge without touching the glyph.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class LiveRingScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun liveRing_idleAndActive() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Box(
                    Modifier
                        .size(width = 220.dp, height = 90.dp)
                        .background(UaTheme.palette.void),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                        CastPeerButton(active = false)
                        CastPeerButton(active = true)
                    }
                }
            }
        }
        // A third into the pulse: the ring has left the button's edge and is still visible.
        composeRule.mainClock.advanceTimeBy(PULSE_SAMPLE_MILLIS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/live_ring.png")
    }

    @androidx.compose.runtime.Composable
    private fun CastPeerButton(active: Boolean) {
        SmallRoundIconButton(
            icon = AppIcons.CastToTv,
            onClick = {},
            tint = if (active) UaTheme.palette.azure else UaTheme.palette.labelPrimary,
            iconSize = CastPeerIconGlyphSize,
            modifier = Modifier.liveRing(active = active, color = UaTheme.palette.azure),
        )
    }

    private companion object {
        const val PULSE_SAMPLE_MILLIS = 460L
    }
}
