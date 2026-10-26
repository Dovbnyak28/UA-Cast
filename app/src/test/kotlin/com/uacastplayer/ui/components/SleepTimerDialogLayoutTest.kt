package com.uacastplayer.ui.components

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.player.SleepTimerDuration
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "en-w640dp-h360dp-land-xhdpi")
@Category(RequiresComposeTestManifest::class)
class SleepTimerDialogLayoutTest(private val locale: String) {
    @get:Rule val rule = createComposeRule()
    @After fun resetFontScale() { RuntimeEnvironment.setFontScale(1f) }

    @Test fun timerOptionsHaveAtLeastFortyEightDpTouchTargets() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp-xhdpi")
        val context = ApplicationProvider.getApplicationContext<Context>()
        rule.setContent { UaCastTheme(AppTheme.CINEMA) { SleepTimerDialog(true, {}, {}, {}) } }
        val labels = SleepTimerDuration.entries.map {
            context.getString(R.string.player_sleep_timer_minutes, it.minutes)
        } + context.getString(R.string.player_sleep_timer_cancel)
        labels.forEach { label ->
            val bounds = rule.onNodeWithText(label).performScrollTo().getUnclippedBoundsInRoot()
            assertTrue("Short touch target for $label: $bounds", bounds.bottom - bounds.top >= 48.dp)
        }
    }

    @Test fun allPresetsAndCancelRemainReachableInLandscapeWithLargeText() {
        RuntimeEnvironment.setQualifiers("$locale-w640dp-h360dp-land-xhdpi")
        RuntimeEnvironment.setFontScale(2f)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val selections = mutableListOf<Int>()
        var cancellations = 0
        var dismissals = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                SleepTimerDialog(true, { selections += it.inWholeMinutes.toInt() },
                    { cancellations++ }, { dismissals++ })
            }
        }
        SleepTimerDuration.entries.forEach { option ->
            clickReadable(context.getString(R.string.player_sleep_timer_minutes, option.minutes))
        }
        clickReadable(context.getString(R.string.player_sleep_timer_cancel))
        if (locale == "uk") {
            rule.onNode(isDialog()).captureRoboImage("src/test/screenshots/sleep_timer_landscape_large_uk.png")
        }
        rule.onNodeWithText(context.getString(R.string.common_close)).assertIsDisplayed().performClick()
        rule.runOnIdle {
            assertEquals(SleepTimerDuration.entries.map { it.minutes }, selections)
            assertEquals(1, cancellations)
            assertEquals(1, dismissals)
        }
    }

    private fun clickReadable(label: String) {
        val node = rule.onNodeWithText(label).performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        node.assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(2f, layout.layoutInput.density.fontScale, 0.01f)
        assertFalse("Clipped timer option: $label", layout.didOverflowHeight)
        node.performClick()
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "locale={0}")
        fun locales() = listOf(arrayOf("en"), arrayOf("uk"), arrayOf("ru"), arrayOf("es"))
    }
}
