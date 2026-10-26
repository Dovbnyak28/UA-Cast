package com.uacastplayer

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Controlled suspended verification; no real PIN, purchase, playlist or network is touched. */
@RunWith(AndroidJUnit4::class)
class MainParentalControlGateInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun dismissedSuccessCannotUnlockANewerAction() {
        verifyDismissedResult(accepted = true)
    }

    @Test fun dismissedFailureCannotMarkANewerDialogIncorrect() {
        verifyDismissedResult(accepted = false)
    }

    private fun verifyDismissedResult(accepted: Boolean) {
        val answer = CompletableDeferred<Boolean>()
        var actions = 0
        lateinit var request: (() -> Unit) -> Unit
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                request = rememberParentalControlGate(false) { answer.await() }
            }
        }
        try {
            rule.runOnIdle { request { actions++ } }
            rule.onNode(hasSetTextAction()).performTextInput("1234")
            rule.onNodeWithText(context.getString(R.string.common_confirm)).performClick()
            rule.onNodeWithText(context.getString(R.string.common_cancel)).performClick()
            rule.runOnIdle { request { actions++ }; answer.complete(accepted) }
            rule.runOnIdle { assertEquals(0, actions) }
            rule.onNodeWithText(context.getString(R.string.parental_control_enter_pin)).assertExists()
            rule.onNodeWithText(context.getString(R.string.parental_control_pin_incorrect)).assertDoesNotExist()
        } finally {
            answer.complete(false)
        }
    }
}
