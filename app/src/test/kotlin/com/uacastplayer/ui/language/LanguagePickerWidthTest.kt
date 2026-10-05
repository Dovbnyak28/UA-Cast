package com.uacastplayer.ui.language

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w891dp-h411dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class LanguagePickerWidthTest {
    @get:Rule val rule = createComposeRule()

    @Test fun continueMatchesTheMaximumFormWidthOnWideShortScreens() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        rule.setContent { UaCastTheme(AppTheme.CINEMA) { LanguagePickerScreen({}) } }
        val button = rule.onNodeWithText(context.getString(R.string.language_picker_continue)).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Continue must align with the 640dp form, not stretch across the display",
            button.width <= 640f * context.resources.displayMetrics.density + 1f)
    }
}
