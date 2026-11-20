package com.uacastplayer.ui.dlna

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.dlna.DlnaDevice
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.UaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "en-w320dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class DlnaLargeTextLayoutTest(private val locale: String) {
    @get:Rule val rule = createComposeRule()

    @Test fun connectionStatusStaysReadableAtDoubleFontScale() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp-xhdpi")
        val context = ApplicationProvider.getApplicationContext<Context>()
        var stops = 0
        val device = DlnaDevice("Living room TV", "http://192.0.2.1/control")
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Surface(Modifier.fillMaxSize(), color = UaTheme.palette.surface2) {
                        DlnaDeviceSheetContent(
                            DlnaConnectionState(connectedDevice = device, playbackStatusUnverified = true, volume = 23),
                            emptyList(), false, {}, { stops++ }, {},
                        )
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(context.getString(R.string.dlna_status_unverified)).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Status squeezed into ${layouts.single().lineCount} short lines", layouts.single().lineCount <= 3)
        assertFalse(layouts.single().hasVisualOverflow)
        if (locale == "uk") rule.onRoot().captureRoboImage("src/test/screenshots/dlna_connected_large_text_uk.png")
        rule.onNodeWithText(context.getString(R.string.dlna_stop_casting)).assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, stops) }
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "locale={0}")
        fun locales() = listOf(arrayOf("en"), arrayOf("uk"), arrayOf("ru"), arrayOf("es"))
    }
}
