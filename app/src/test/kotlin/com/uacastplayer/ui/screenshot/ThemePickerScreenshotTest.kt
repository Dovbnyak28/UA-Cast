package com.uacastplayer.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.components.SegmentedControl
import com.uacastplayer.ui.settings.nameRes
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.UaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden coverage for the theme picker specifically, because adding a theme narrows it.
 *
 * [SegmentedControl] gives every segment `weight(1f)` and centres a label in it, and the label has
 * no `maxLines`. A weight decides how much room a segment gets; it cannot create a place to wrap -
 * so a label that outgrows one third of the row does not get truncated, it silently goes two lines
 * tall and takes the whole control with it. Three themes means each segment is a third of the row
 * instead of a half, and the longest labels ("Опівніч", "Medianoche") are the new ones.
 *
 * Rendered off the same [nameRes] the settings screen uses, in the widest-label locales, at the
 * narrowest width the app supports. See [DesignSystemScreenshotTest] for record/verify commands.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class ThemePickerScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String) {
        composeRule.setContent {
            UaCastTheme(AppTheme.MIDNIGHT) {
                Box(
                    Modifier
                        .size(width = 411.dp, height = 96.dp)
                        .background(UaTheme.palette.void)
                        .padding(16.dp),
                ) {
                    SegmentedControl(
                        options = AppTheme.entries.map { stringResource(it.nameRes()) },
                        selectedIndex = AppTheme.entries.indexOf(AppTheme.MIDNIGHT),
                        onSelected = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    @Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
    fun themePicker_ukrainian() = capture("theme_picker_uk")

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-xhdpi")
    fun themePicker_spanish() = capture("theme_picker_es")

    @Test
    @Config(qualifiers = "ru-w411dp-h891dp-xhdpi")
    fun themePicker_russian() = capture("theme_picker_ru")

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun themePicker_english() = capture("theme_picker_en")
}
