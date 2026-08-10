package com.uacastplayer.ui.layout

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.language.LanguagePickerScreen
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What survives the Activity being destroyed and rebuilt from its saved state, on the two screens a
 * first install has to walk through before it can do anything.
 *
 * Rotation is *not* the trigger to think about here: `MainActivity` lists `orientation|screenSize`
 * in its `configChanges`, so it handles a rotation itself and nothing is recreated. What does
 * recreate it is a process death after the app has been in the background, the developer option
 * "don't keep activities", and - easy to overlook - a change to font size or display size, since
 * `fontScale` and `density` are not in that `configChanges` list.
 *
 * [StateRestorationTester] reproduces exactly that: it tears the composition down and rebuilds it
 * from the saved-state registry, which is what `rememberSaveable` writes to and plain `remember`
 * does not. Both of these assertions fail against `remember`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class StateRestorationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun languagePicker_keepsTheChosenLanguage() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            UaCastTheme(AppTheme.CINEMA) { LanguagePickerScreen(onLanguageConfirmed = {}) }
        }

        composeRule.onNodeWithText("Продовжити").assertIsNotEnabled()
        composeRule.onNodeWithText("Українська").performClick()
        composeRule.onNodeWithText("Продовжити").assertIsEnabled()

        restorationTester.emulateSavedInstanceStateRestore()

        // Without the selection, Continue is disabled again and the screen looks like the tap never
        // happened - on the very first screen of a new install.
        composeRule.onNodeWithText("Продовжити").assertIsEnabled()
    }

    // The onboarding walkthrough this file used to cover here is gone, replaced by the guided tour.
    // There is deliberately no equivalent test: the tour's position is not composition state at
    // all - it lives in `app/GuidedTourController`, held by AppViewModel, so a configuration change
    // cannot lose it and there is nothing for StateRestorationTester to exercise. Process death
    // does end the tour, and that is the intended behaviour: the completion flag was never written,
    // so the next launch offers it again from the welcome card rather than resuming mid-step.
}
