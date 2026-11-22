package app.zorik.player.data.prefs

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A locale override must inherit window size, font scaling and system light/dark changes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class LocalizedConfigurationInheritanceTest {
    @Test fun changingTheLanguageDoesNotPinAccessibilityOrWindowConfiguration() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        var applied: Configuration? = null
        val context = object : ContextWrapper(base) {
            override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
                applied = Configuration(overrideConfiguration)
                return base.createConfigurationContext(overrideConfiguration)
            }
        }
        context.withAppLocale()
        val override = checkNotNull(applied)
        assertEquals("The override must inherit system font scale", 0f, override.fontScale, 0f)
        assertEquals(Configuration.DENSITY_DPI_UNDEFINED, override.densityDpi)
        assertEquals(Configuration.SCREEN_WIDTH_DP_UNDEFINED, override.screenWidthDp)
        assertEquals(Configuration.SCREEN_HEIGHT_DP_UNDEFINED, override.screenHeightDp)
        assertEquals(Configuration.ORIENTATION_UNDEFINED, override.orientation)
        assertEquals(Configuration.UI_MODE_TYPE_UNDEFINED, override.uiMode)
        assertNotNull(override.locales[0])
        assertEquals(context.currentAppLanguage().code, override.locales[0].language)
    }
}
