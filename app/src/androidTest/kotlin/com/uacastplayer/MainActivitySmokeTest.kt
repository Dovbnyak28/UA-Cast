package com.uacastplayer

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uacastplayer.testsupport.awaitComposeHierarchy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bare-minimum launch smoke test: MainActivity starts and renders *something* without crashing.
 * Deliberately doesn't assert which screen (language picker vs. Home) shows, since that depends
 * on persisted state the test doesn't control. The same test runs in the API 24/30/36 emulator
 * matrix and through scripts/run-instrumented-tests.sh on a connected handset.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityLaunchesAndRendersAScreen() {
        // ActivityScenario startup is asynchronous on a cold/OEM device. Waiting for idle alone
        // does not guarantee that setContent has registered its semantics owner.
        composeTestRule.awaitComposeHierarchy()
        // Throws if no semantics tree exists yet, i.e. nothing rendered - a plain existence check
        // without depending on which screen (language picker vs. Home) happened to show first.
        composeTestRule.onRoot().fetchSemanticsNode()
    }
}
