package com.uacastplayer.ui.screenshot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.language.LanguagePickerScreen
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The first screen a new install shows, pinned at a **short** viewport.
 *
 * This exists because of a bug that portrait could not reveal: the screen's `LazyColumn` had no
 * `weight(1f)`, so it took the whole remaining height of its `Column` and left the Continue button
 * with none. In portrait there was room for both and everything looked correct; in landscape the
 * button fell out of the layout entirely and a fresh install could not get past its first screen -
 * the list scrolled, and there was no Continue to reach at the end of it.
 *
 * So the qualifier here is the test. A golden at portrait height would have passed before the fix
 * and proves nothing; `h411dp` is the constraint that made the defect visible in the first place.
 * If the weight is ever dropped again, the button vanishes from this image.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w891dp-h411dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class LanguagePickerScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun languagePicker_shortViewport_keepsContinueReachable() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                LanguagePickerScreen(onLanguageConfirmed = {})
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/language_picker_short.png")
    }
}
