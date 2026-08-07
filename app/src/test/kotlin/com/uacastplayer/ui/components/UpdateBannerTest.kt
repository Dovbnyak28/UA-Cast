package com.uacastplayer.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.update.AppVersion
import com.uacastplayer.update.GitHubRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The update banner is the entire "notify" half of the update feature - if it does not appear, or
 * appears and cannot be acted on, nothing else in the feature matters.
 *
 * The viewport is deliberately the tight one from `FontScaleLayoutTest`: a 4.5" phone, which is
 * also what any phone becomes at the largest Display Size setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class UpdateBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val release = GitHubRelease(
        version = AppVersion.parse("v1.4.0")!!,
        tagName = "v1.4.0",
        releaseUrl = "https://github.com/Dovbnyak28/UA-Cast/releases/tag/v1.4.0",
        notes = "Some notes",
    )

    @Test
    fun nothingIsShownWhenThereIsNoUpdate() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                UpdateBanner(release = null, onOpen = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithTag(UiTestTags.UPDATE_BANNER).assertDoesNotExist()
    }

    @Test
    fun theVersionIsNamedSoTheUserKnowsWhatTheyAreBeingOffered() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                UpdateBanner(release = release, onOpen = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithTag(UiTestTags.UPDATE_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("Доступна нова версія").assertIsDisplayed()
        composeRule.onNodeWithText("Версію v1.4.0 можна завантажити.").assertIsDisplayed()
    }

    /** The one thing the banner exists to do. A URL other than the release's own would send the
     * user to the wrong page, which no amount of the banner "appearing correctly" would catch. */
    @Test
    fun theActionOpensThatReleasesOwnPage() {
        var opened: String? = null
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                UpdateBanner(release = release, onOpen = { opened = it }, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Відкрити реліз").performClick()

        assertEquals("https://github.com/Dovbnyak28/UA-Cast/releases/tag/v1.4.0", opened)
    }

    @Test
    fun closingReportsTheDismissalExactlyOnce() {
        var dismissals = 0
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                UpdateBanner(release = release, onOpen = {}, onDismiss = { dismissals++ })
            }
        }

        composeRule.onNodeWithContentDescription("Приховати").performClick()

        assertEquals(1, dismissals)
    }

    /** The banner is part of the layout rather than an overlay, so it pushes the screen down - and
     * at 2.0x text on a small screen its own action must still be reachable inside the viewport. */
    @Test
    fun theActionStaysOnScreenAtTheLargestFontScale() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2.0f)) {
                UaCastTheme(AppTheme.CINEMA) {
                    UpdateBanner(release = release, onOpen = {}, onDismiss = {})
                }
            }
        }

        val button = composeRule.onNodeWithText("Відкрити реліз")
        button.assertIsDisplayed()

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val bounds = button.getUnclippedBoundsInRoot()
        val slack = 0.5.dp
        assertTrue(
            "the update action is cut off at 2.0x: $bounds vs viewport $root",
            bounds.left >= root.left - slack &&
                bounds.top >= root.top - slack &&
                bounds.right <= root.right + slack &&
                bounds.bottom <= root.bottom + slack,
        )
    }

    /** Content is read from the last non-null release while the container animates away; reading
     * the incoming null instead blanked the text out for the length of the exit animation. */
    @Test
    fun theTextSurvivesTheReleaseBeingClearedForTheExitAnimation() {
        val current = mutableStateOf<GitHubRelease?>(release)
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                UpdateBanner(release = current.value, onOpen = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("Версію v1.4.0 можна завантажити.").assertIsDisplayed()

        composeRule.mainClock.autoAdvance = false
        current.value = null
        composeRule.mainClock.advanceTimeByFrame()

        // Mid-exit: still the old text, not an empty line.
        composeRule.onNodeWithText("Версію v1.4.0 можна завантажити.").assertExists()
    }
}
