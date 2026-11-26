package app.zorik.player.ui.guidedtour

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.zorik.player.guidedtour.GuidedTip
import app.zorik.player.guidedtour.GuidedTourKeys
import app.zorik.player.guidedtour.GuidedTourPhase
import app.zorik.player.guidedtour.GuidedTourState
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.CardTitle
import app.zorik.player.ui.theme.UaTheme
import app.zorik.player.ui.theme.ZorikTheme
import app.zorik.player.ui.theme.appBackground
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the intro and a tip look like, on a mid-size phone: every intro page on the default theme,
 * the first one on the light theme and sideways, and the player tip over a stand-in player page.
 *
 * The pictures are drawn in code from palette colours, so these goldens are also what catches a
 * theme change that leaves one of them unreadable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class GuidedTourScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun introPages() {
        val page = androidx.compose.runtime.mutableIntStateOf(0)
        composeRule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                GuidedTourOverlay(
                    state = GuidedTourState(phase = GuidedTourPhase.INTRO, introPage = page.intValue),
                    callbacks = GuidedTourCallbacks(),
                )
            }
        }
        listOf("intro_playlist_cinema", "intro_watch_cinema", "intro_cast_cinema").forEachIndexed { index, name ->
            page.intValue = index
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        }
    }

    @Test
    fun introOnTheLightTheme() {
        composeRule.setContent {
            ZorikTheme(AppTheme.DAYLIGHT) {
                GuidedTourOverlay(
                    state = GuidedTourState(phase = GuidedTourPhase.INTRO),
                    callbacks = GuidedTourCallbacks(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/intro_playlist_daylight.png")
    }

    @Test
    fun introSideways() {
        RuntimeEnvironment.setQualifiers("uk-w891dp-h411dp-xhdpi")
        composeRule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                GuidedTourOverlay(
                    state = GuidedTourState(phase = GuidedTourPhase.INTRO, introPage = 2),
                    callbacks = GuidedTourCallbacks(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/intro_cast_landscape_cinema.png")
    }

    @Test
    fun playerTipOverThePlayerPage() {
        composeRule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                GuidedTourHost(
                    state = GuidedTourState(phase = GuidedTourPhase.TIP, tip = GuidedTip.PLAYER),
                    callbacks = GuidedTourCallbacks(),
                ) {
                    StandInPlayerPage()
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/tip_player_cinema.png")
    }

    /** Enough of a player page for the spotlight to have something to frame. */
    @androidx.compose.runtime.Composable
    private fun StandInPlayerPage() {
        Column(
            modifier = Modifier.fillMaxSize().appBackground().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.height(48.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .guidedTourTarget(GuidedTourKeys.PLAYER_VIDEO),
            )
            Text("Channel 1", style = CardTitle, color = UaTheme.palette.labelPrimary)
        }
    }
}
