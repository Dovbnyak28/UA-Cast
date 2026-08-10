package com.uacastplayer.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.language.LanguagePickerScreen
import com.uacastplayer.guidedtour.GuidedTourPhase
import com.uacastplayer.guidedtour.GuidedTourState
import com.uacastplayer.guidedtour.GuidedTourSteps
import com.uacastplayer.ui.guidedtour.GuidedTourOverlay
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two screens a first install cannot get past, held at the smallest viewport this app supports
 * and pushed through every font scale Android offers.
 *
 * Both are a `Column` ending in a primary button, which is the layout that fails quietly: the text
 * above the button grows with the font scale, the button keeps its size, and past some scale it is
 * simply pushed off the bottom. Nothing crashes and nothing is logged - the user just cannot
 * continue, and a person who needs 2.0x text is exactly the person who cannot work around it.
 *
 * That is not hypothetical here: `LanguagePickerScreenshotTest` exists because this same button
 * disappeared once already, at 1.0x on a short landscape viewport. This covers the other axis.
 *
 * The viewport is `w320dp-h480dp` - a 4.5" phone, and also what a modern phone becomes at the
 * largest Display Size setting, since that setting works by raising density and so shrinks the dp
 * viewport. Font scale is applied through [LocalDensity] because that is the exact channel Android
 * uses to scale `sp` in Compose; `@Config(qualifiers)` has no font-scale axis.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class FontScaleLayoutTest(private val fontScale: Float) {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContentAtScale(content: @Composable () -> Unit) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                UaCastTheme(AppTheme.CINEMA) { content() }
            }
        }
    }

    /**
     * Fails if [text] is off-screen, or on-screen but with any edge past the viewport - the second
     * case is the one `assertIsDisplayed` alone lets through, and a half-visible button is not a
     * usable one.
     */
    private fun assertFullyOnScreen(text: String) {
        val node = composeRule.onNodeWithText(text)
        node.assertIsDisplayed()

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val bounds = node.getUnclippedBoundsInRoot()
        val slack = 0.5.dp

        assertTrue(
            "at fontScale $fontScale '$text' is cut off: $bounds vs viewport $root",
            bounds.left >= root.left - slack &&
                bounds.top >= root.top - slack &&
                bounds.right <= root.right + slack &&
                bounds.bottom <= root.bottom + slack,
        )
    }

    @Test
    fun languagePicker_continueStaysOnScreen() {
        setContentAtScale { LanguagePickerScreen(onLanguageConfirmed = {}) }
        assertFullyOnScreen("Продовжити")
    }

    /**
     * The guided tour's step card is the densest row of controls in the app - Skip, Back and Next
     * side by side - and it replaced the onboarding walkthrough this test used to cover. At 2.0x on
     * a 4.5" viewport that row is the first thing that would push its primary action off the edge.
     */
    @Test
    fun guidedTour_nextButtonStaysOnScreen() {
        setContentAtScale {
            GuidedTourOverlay(
                state = GuidedTourState(
                    phase = GuidedTourPhase.STEPS,
                    stepIndex = 0,
                    steps = GuidedTourSteps.DEFAULT,
                ),
                onNext = {},
                onBack = {},
                onSkip = {},
                onComplete = {},
            )
        }
        assertFullyOnScreen("Далі")
    }

    @Test
    fun guidedTour_welcomeStartButtonStaysOnScreen() {
        setContentAtScale {
            GuidedTourOverlay(
                state = GuidedTourState(phase = GuidedTourPhase.WELCOME, steps = GuidedTourSteps.DEFAULT),
                onNext = {},
                onBack = {},
                onSkip = {},
                onComplete = {},
            )
        }
        assertFullyOnScreen("Почати")
    }

    companion object {
        /** Every scale Android's Settings > Display > Font size offers, smallest to largest. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "fontScale={0}")
        fun fontScales(): List<Array<Any>> =
            listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 2.0f).map { arrayOf<Any>(it) }
    }
}
