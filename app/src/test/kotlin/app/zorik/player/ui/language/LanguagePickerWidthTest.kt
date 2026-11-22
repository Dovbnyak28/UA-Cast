package app.zorik.player.ui.language

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.zorik.player.R
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w891dp-h411dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class LanguagePickerWidthTest {
    @get:Rule val rule = createComposeRule()

    @Test fun continueMatchesTheMaximumFormWidthOnWideShortScreens() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        rule.setContent { ZorikTheme(AppTheme.CINEMA) { LanguagePickerScreen({}) } }
        val button = rule.onNodeWithText(context.getString(R.string.language_picker_continue)).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Continue must align with the 640dp form, not stretch across the display",
            button.width <= 640f * context.resources.displayMetrics.density + 1f)
    }
}
