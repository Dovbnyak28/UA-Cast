package app.zorik.player.ui.screenshot

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
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.settings.SettingsChoiceRow
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.R
import app.zorik.player.ui.settings.nameRes
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import app.zorik.player.ui.theme.UaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production theme choice stays readable at narrow widths in all supported locales. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class ThemePickerScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String) {
        composeRule.setContent {
            ZorikTheme(AppTheme.MIDNIGHT) {
                Box(
                    Modifier
                        .size(width = 411.dp, height = 96.dp)
                        .background(UaTheme.palette.void)
                        .padding(16.dp),
                ) {
                    SettingsChoiceRow(
                        label = stringResource(R.string.settings_theme_label), icon = AppIcons.Image,
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
