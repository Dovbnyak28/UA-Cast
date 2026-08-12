package com.uacastplayer.ui.nav

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uacastplayer.MainActivity
import com.uacastplayer.R
import com.uacastplayer.testsupport.skipOnboarding
import com.uacastplayer.ui.UiTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What is still on screen after one of the full-screen overlays closes.
 *
 * Help, Terms and the add-playlist screen do not draw *over* the tab scaffold the way the player
 * does - they replace it, in a `when` in `MainActivity.ScaffoldZone`. `RootScaffold` holds the
 * selected tab as its own `rememberSaveable`, and `rememberSaveable` only outlives a composable
 * that leaves composition if something is holding it. Nothing was. So opening Help from Settings
 * and pressing back put the user on Home, along with whatever else the tab they left had on screen:
 * the opened group, the group grid's scroll position, both search boxes.
 *
 * The fix wraps that branch in a `SaveableStateProvider`. This is the test that says so, and it has
 * to be an instrumented one: the defect is about a real Activity's composition being torn down and
 * rebuilt, and `MainActivity` brings the Cast SDK with it, which is not something to stand up under
 * Robolectric for a navigation assertion.
 *
 * Deliberately read-only - it clicks through the app and presses back, and touches no persisted
 * state. There is nothing here to capture and restore, unlike `EmptyPlaylistInstrumentedTest`.
 */
@RunWith(AndroidJUnit4::class)
class OverlayReturnInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearGates() {
        composeTestRule.activity.let(::skipOnboarding)
        composeTestRule.waitForIdle()
    }

    @Test
    fun returningFromHelp_landsBackOnSettingsRatherThanHome() {
        val activity = composeTestRule.activity
        val helpLabel = activity.getString(R.string.settings_open_help)

        openSettingsTab()
        // The row is well down a long scrolling column on a phone-sized screen.
        composeTestRule.onNodeWithText(helpLabel).performScrollTo()
        composeTestRule.waitForIdle()

        // By tag, because nothing else points at this button: its own label is the generic,
        // localised "Open" that the Terms row beside it also carries, and the enclosing Row emits
        // no semantics node - so on the real device this button reported eighty siblings and
        // "hasAnySibling(Detailed help)" matched three separate buttons.
        composeTestRule.onNodeWithTag(UiTestTags.SETTINGS_OPEN_HELP_BUTTON).performClick()
        composeTestRule.waitForIdle()

        // Precondition, not the assertion: if Help never opened, everything below proves nothing.
        composeTestRule.onNodeWithText(activity.getString(R.string.help_title)).assertExists()

        Espresso.pressBack()
        composeTestRule.waitForIdle()

        // The Settings screen is the only place this string exists, so finding it is finding the
        // tab. Asserting on the tab bar's own "Settings" label instead would have matched whether
        // or not the tab was selected.
        composeTestRule.onNodeWithText(helpLabel).assertExists()
    }

    private fun openSettingsTab() {
        composeTestRule.onNode(
            hasText(composeTestRule.activity.getString(R.string.nav_settings)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).performClick()
        composeTestRule.waitForIdle()
    }
}
