package com.uacastplayer.ui.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.R
import com.uacastplayer.parentalcontrol.ParentalControlPinPolicy
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Getting a second guess at the parental-control PIN.
 *
 * The bug: clearing the field was an effect keyed on the dialog's `isError` flag, and the caller
 * (`MainActivity.rememberParentalControlGate`) sets that flag to true on every wrong guess. So it
 * changed on the first wrong guess and on no other, and the second guess's four digits stayed in
 * the field. Input is capped at [ParentalControlPinPolicy.PIN_LENGTH], so with four characters
 * still in it every further digit is refused outright: Confirm stays enabled, resubmits the same
 * wrong PIN, and nothing on screen changes because the error text was already showing. Behind a
 * mask that shows dots either way, the only way out was to guess the field was not empty and
 * backspace four times.
 *
 * **Instrumented rather than a Robolectric unit test, and that is a harness limit, not a choice.**
 * Under Robolectric a text field *inside an `AlertDialog`* never lets Compose reach idle -
 * `setContent` itself times out with `AppNotIdleException` after 60s. Isolated with a three-case
 * probe on that harness: an `AlertDialog` with no text field settles, a bare `OutlinedTextField`
 * settles, and the two together do not. Pausing the animation clock does not help. So the dialog
 * this is about cannot be composed there at all, and this suite - which runs on a real device - is
 * where it can.
 *
 * Read-only: composes one dialog, touches no persisted state, needs no playlist.
 */
@RunWith(AndroidJUnit4::class)
class ParentalControlPinRetryInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val confirmLabel: String get() = context.getString(R.string.common_confirm)

    private val submitted = mutableListOf<String>()
    private var isError by mutableStateOf(false)

    private fun showDialog() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                ParentalControlPinDialog(
                    title = "PIN",
                    isError = isError,
                    // Every guess is wrong - that is the situation being tested, and it is what
                    // pins `isError` true from the first guess onwards.
                    onSubmit = { pin ->
                        submitted += pin
                        isError = true
                    },
                    onDismiss = {},
                )
            }
        }
        // setContent schedules the first composition; wait before querying the field so a slow
        // device cannot turn a valid dialog test into a false "No compose hierarchies" failure.
        composeRule.waitForIdle()
    }

    private fun guess(pin: String) {
        composeRule.onNode(hasSetTextAction()).performTextInput(pin)
        composeRule.onNodeWithText(confirmLabel).performClick()
        composeRule.waitForIdle()
    }

    /** Three guesses, not two: the first one is exactly the case that already worked. */
    @Test
    fun everyWrongGuessEmptiesTheField_notOnlyTheFirst() {
        showDialog()

        guess("1111")
        guess("2222")
        guess("3333")

        assertEquals(listOf("1111", "2222", "3333"), submitted)
    }

    /**
     * The control for the test above. Without it, a change that simply stopped emptying the field
     * altogether would look the same from here - the first guess would still be reported correctly.
     */
    @Test
    fun theFieldIsEmptyAgainRightAfterAGuessIsTaken() {
        showDialog()

        guess("1111")

        composeRule.onNodeWithText(confirmLabel).assertIsNotEnabled()
    }

    /**
     * Confirm is gated on a complete PIN. Stated here so the dead end above reads as a consequence
     * of two rules that are both visible, rather than as a claim about one.
     */
    @Test
    fun confirmIsEnabledExactlyWhenTheFieldHoldsAFullPin() {
        showDialog()

        composeRule.onNodeWithText(confirmLabel).assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("12")
        composeRule.onNodeWithText(confirmLabel).assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("34")
        composeRule.onNodeWithText(confirmLabel).assertIsEnabled()
    }

    /** The cap that turns an uncleared field into a dead end - a fifth digit is refused, so a full
     * field cannot be typed over. */
    @Test
    fun aFifthDigitIsRefusedRatherThanReplacingTheField() {
        showDialog()

        composeRule.onNode(hasSetTextAction()).performTextInput("1234")
        composeRule.onNode(hasSetTextAction()).performTextInput("5")
        composeRule.onNodeWithText(confirmLabel).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("1234"), submitted)
    }
}
