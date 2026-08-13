package com.uacastplayer.ui.components

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What happens when the battery-optimization settings screen is not there to open.
 *
 * `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` is a documented action, not a guaranteed
 * one: a device whose Settings app does not export it - a stripped or replaced Settings, a custom
 * ROM, a manufacturer that moved battery management somewhere of its own - resolves nothing, and
 * `startActivity` on an unresolvable intent throws `ActivityNotFoundException`, which is
 * unchecked. This dialog is shown the first time a cast session connects, so the crash would land
 * in the middle of the app's main feature, on a hint the user never asked for.
 *
 * This is the same failure the rest of the app already guards against, in three places and with
 * three different mechanisms: [com.uacastplayer.ui.diagnostics.sendDiagnostics] catches
 * `ActivityNotFoundException` around its share intent, `MainActivity` opens the release page
 * through `LocalUriHandler` rather than a raw ACTION_VIEW ("a device with no browser at all - a TV
 * box, say"), and `CastProxyService` wraps its own start in `startForegroundServiceSafely`. Only
 * this call site was left bare.
 *
 * [org.robolectric.shadows.ShadowApplication.checkActivities] is what makes the test device one of
 * those devices: with it on, Robolectric refuses an intent nothing resolves, exactly as a real one
 * does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class BatteryOptimizationDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    private fun allowLabel(): String = application.getString(R.string.battery_hint_allow)

    @Test
    fun `a device with no battery settings screen must not take the app down`() {
        shadowOf(application).checkActivities(true)
        var allowed = false
        composeRule.setContent {
            UaCastTheme(AppTheme.MIDNIGHT) {
                BatteryOptimizationDialog(onAllow = { allowed = true }, onDismiss = {})
            }
        }

        composeRule.onNodeWithText(allowLabel()).performClick()
        composeRule.waitForIdle()

        // Not just "did not crash": the hint is a one-shot, and the flag that retires it is set by
        // onAllow. Losing it would bring this dialog back on every single cast session.
        assertTrue("the one-time hint must still be retired when the screen cannot be opened", allowed)
    }

    /** The ordinary device, so the guard is not mistaken for the behaviour. */
    @Test
    fun `a device that has the screen still opens it and retires the hint`() {
        var allowed = false
        composeRule.setContent {
            UaCastTheme(AppTheme.MIDNIGHT) {
                BatteryOptimizationDialog(onAllow = { allowed = true }, onDismiss = {})
            }
        }

        composeRule.onNodeWithText(allowLabel()).performClick()
        composeRule.waitForIdle()

        val started = shadowOf(application).nextStartedActivity
        assertTrue("the battery settings screen should have been asked for", started != null)
        assertTrue(allowed)
    }
}
