package com.uacastplayer

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bare-minimum launch smoke test: MainActivity starts and renders *something* without crashing.
 * Deliberately doesn't assert which screen (language picker vs. Home) shows, since that depends
 * on persisted state the test doesn't control. Not run in CI (no emulator there) - see
 * android-ci.yml, which only compiles this via :app:assembleDebugAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityLaunchesAndRendersAScreen() {
        // ActivityScenario startup is asynchronous on a cold device. The rule can enter the test
        // method before MainActivity has reached RESUMED (and before setContent has registered its
        // semantics owner), so querying the root immediately races the launch and reports the
        // misleading "No compose hierarchies" error.
        composeTestRule.waitForIdle()
        // Throws if no semantics tree exists yet, i.e. nothing rendered - a plain existence check
        // without depending on which screen (language picker vs. Home) happened to show first.
        composeTestRule.onRoot().fetchSemanticsNode()
    }
}
