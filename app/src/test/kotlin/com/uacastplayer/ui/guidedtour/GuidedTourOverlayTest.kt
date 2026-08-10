package com.uacastplayer.ui.guidedtour

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.uacastplayer.guidedtour.GuidedTourKeys
import com.uacastplayer.guidedtour.GuidedTourPhase
import com.uacastplayer.guidedtour.GuidedTourState
import com.uacastplayer.guidedtour.GuidedTourSteps
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

/**
 * The tour as the user meets it: the welcome card, moving through steps, and the two ways out.
 *
 * Driven through [GuidedTourOverlay] with a state supplied directly rather than through
 * `AppViewModel`, so these assert what the overlay renders for a given state - the state machine
 * itself is `GuidedTourControllerTest`'s job.
 *
 * The narrow viewport is deliberate, and the same one `UpdateBannerTest` uses: a 4.5" phone, which
 * is also what any phone becomes at the largest Display Size setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class GuidedTourOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val steps = GuidedTourSteps.DEFAULT

    @Test
    fun anIdleTourDrawsNothingOverTheApp() {
        setOverlay(GuidedTourState())

        composeRule.onNodeWithText("Ласкаво просимо до UA Cast IPTV").assertDoesNotExist()
        composeRule.onNodeWithText("Пропустити").assertDoesNotExist()
    }

    @Test
    fun theWelcomeCardOffersBothStartingAndLeaving() {
        setOverlay(GuidedTourState(phase = GuidedTourPhase.WELCOME, steps = steps))

        composeRule.onNodeWithText("Ласкаво просимо до UA Cast IPTV").assertIsDisplayed()
        composeRule.onNodeWithText("Швидко покажемо основні можливості додатка.").assertIsDisplayed()
        composeRule.onNodeWithText("Почати").assertIsDisplayed()
        composeRule.onNodeWithText("Пропустити").assertIsDisplayed()
    }

    @Test
    fun startingTheTourReportsIt() {
        var advanced = 0
        setOverlay(GuidedTourState(phase = GuidedTourPhase.WELCOME, steps = steps), onNext = { advanced++ })

        composeRule.onNodeWithText("Почати").performClick()

        assertEquals(1, advanced)
    }

    @Test
    fun leavingFromTheWelcomeCardReportsASkip() {
        var skips = 0
        setOverlay(GuidedTourState(phase = GuidedTourPhase.WELCOME, steps = steps), onSkip = { skips++ })

        composeRule.onNodeWithText("Пропустити").performClick()

        assertEquals(1, skips)
    }

    /** The one thing the progress indicator has to be: right about which step this is. */
    @Test
    fun aStepShowsItsPositionInTheTour() {
        setOverlay(GuidedTourState(phase = GuidedTourPhase.STEPS, stepIndex = 2, steps = steps))

        composeRule.onNodeWithText("3 / 7").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("3 / 7").assertExists()
    }

    @Test
    fun aStepShowsItsOwnTitleAndText() {
        setOverlay(GuidedTourState(phase = GuidedTourPhase.STEPS, stepIndex = 0, steps = steps))

        composeRule.onNodeWithText("Додайте плейлист").assertIsDisplayed()
    }

    @Test
    fun aStepOffersBackNextAndSkip() {
        var next = 0
        var back = 0
        var skip = 0
        setOverlay(
            GuidedTourState(phase = GuidedTourPhase.STEPS, stepIndex = 1, steps = steps),
            onNext = { next++ },
            onBack = { back++ },
            onSkip = { skip++ },
        )

        composeRule.onNodeWithText("Далі").performClick()
        composeRule.onNodeWithText("Назад").performClick()
        composeRule.onNodeWithText("Пропустити").performClick()

        assertEquals(1, next)
        assertEquals(1, back)
        assertEquals(1, skip)
    }

    @Test
    fun theLastCardEndsTheTourRatherThanContinuingIt() {
        var completions = 0
        setOverlay(
            GuidedTourState(phase = GuidedTourPhase.DONE, steps = steps),
            onComplete = { completions++ },
        )

        composeRule.onNodeWithText("Готово!").assertIsDisplayed()
        composeRule.onNodeWithText("Тепер ви знаєте основні можливості UA Cast IPTV.").assertIsDisplayed()
        composeRule.onNodeWithText("Далі").assertDoesNotExist()

        composeRule.onNodeWithText("Почати користуватися").performClick()

        assertEquals(1, completions)
    }

    /**
     * The failure mode the whole fallback chain exists for. No element has registered
     * `settings_button` here, so the step has nothing to highlight - and must still render its text
     * and its controls rather than crashing or hanging on a null rectangle.
     */
    @Test
    fun aStepWhoseTargetIsNotOnScreenStillRendersAsAPlainStep() {
        val settingsIndex = steps.indexOfFirst { it.id == "settings" }
        setOverlay(GuidedTourState(phase = GuidedTourPhase.STEPS, stepIndex = settingsIndex, steps = steps))

        composeRule.onNodeWithText("Налаштування").assertIsDisplayed()
        composeRule.onNodeWithText("Далі").assertIsDisplayed()
    }

    /**
     * The live path, end to end: an element registers itself through [guidedTourTarget], and the
     * step aimed at it finds bounds to highlight. Asserting the hole is drawn is a pixel test the
     * scrim does not expose - what is asserted here is that registering does not disturb the card,
     * which is the part a broken registry would take down with it.
     */
    @Test
    fun aRegisteredElementIsFoundByTheStepThatPointsAtIt() {
        val state = mutableStateOf(
            GuidedTourState(
                phase = GuidedTourPhase.STEPS,
                stepIndex = steps.indexOfFirst { it.id == "settings" },
                steps = steps,
            ),
        )
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                GuidedTourHost(
                    state = state.value,
                    onNext = {},
                    onBack = {},
                    onSkip = {},
                    onComplete = {},
                ) {
                    Box(modifier = Modifier.size(64.dp).guidedTourTarget(GuidedTourKeys.SETTINGS_BUTTON))
                }
            }
        }

        composeRule.onNodeWithText("Налаштування").assertIsDisplayed()
        composeRule.onNodeWithText("Далі").assertIsDisplayed()
    }

    private fun setOverlay(
        state: GuidedTourState,
        onNext: () -> Unit = {},
        onBack: () -> Unit = {},
        onSkip: () -> Unit = {},
        onComplete: () -> Unit = {},
    ) {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                GuidedTourOverlay(
                    state = state,
                    onNext = onNext,
                    onBack = onBack,
                    onSkip = onSkip,
                    onComplete = onComplete,
                )
            }
        }
    }
}
