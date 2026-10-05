package com.uacastplayer

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w320dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class MainParentalControlGateTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dismissedVerificationCannotExecuteANewRequest() {
        val answer = CompletableDeferred<Boolean>()
        var firstActions = 0
        var newActions = 0
        lateinit var request: (() -> Unit) -> Unit
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                request = rememberParentalControlGate(false) { answer.await() }
            }
        }
        try {
            rule.runOnIdle { request { firstActions++ } }
            submitPin()
            rule.onNodeWithText("Cancel").performClick()
            rule.runOnIdle { request { newActions++ }; answer.complete(true) }
            rule.runOnIdle {
                assertEquals(0, firstActions)
                assertEquals(0, newActions)
            }
            rule.onNodeWithText("Enter PIN").assertExists()
        } finally {
            answer.complete(false)
        }
    }

    @Test fun dismissedFailureCannotAddAnErrorToANewDialog() {
        val answer = CompletableDeferred<Boolean>()
        lateinit var request: (() -> Unit) -> Unit
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                request = rememberParentalControlGate(false) { answer.await() }
            }
        }
        try {
            rule.runOnIdle { request {} }
            submitPin()
            rule.onNodeWithText("Cancel").performClick()
            rule.runOnIdle { request {}; answer.complete(false) }
            rule.onNodeWithText("Incorrect PIN").assertDoesNotExist()
            rule.onNodeWithText("Enter PIN").assertExists()
        } finally {
            answer.complete(false)
        }
    }

    @Test fun aCurrentSuccessfulVerificationExecutesTheCapturedActionExactlyOnce() {
        var actions = 0
        lateinit var request: (() -> Unit) -> Unit
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                request = rememberParentalControlGate(false) { true }
            }
        }
        rule.runOnIdle { request { actions++ } }
        submitPin()
        rule.runOnIdle { assertEquals(1, actions) }
        rule.onNodeWithText("Enter PIN").assertDoesNotExist()
    }

    @Test fun aSuccessfulActionCanOpenAnotherGateWithoutLosingItsPendingAction() {
        var actions = 0
        lateinit var request: (() -> Unit) -> Unit
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                request = rememberParentalControlGate(false) { true }
            }
        }
        rule.runOnIdle { request { request { actions++ } } }
        submitPin()
        rule.onNodeWithText("Enter PIN").assertExists()
        submitPin()
        rule.runOnIdle { assertEquals(1, actions) }
        rule.onNodeWithText("Enter PIN").assertDoesNotExist()
    }

    private fun submitPin() {
        rule.onNode(hasSetTextAction()).performTextInput("1234")
        rule.onNodeWithText("Confirm").performClick()
    }
}
