package app.zorik.player.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.components.ComponentCatalogPreview
import app.zorik.player.ui.components.PrimaryButton
import app.zorik.player.ui.components.SecondaryButton
import app.zorik.player.ui.components.UaCard
import app.zorik.player.ui.components.UaListItem
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.LocalReducedEffects
import app.zorik.player.ui.theme.ZorikTheme
import app.zorik.player.ui.tv.TvInputRegistry
import app.zorik.player.ui.tv.TvPresentation
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w320dp-h900dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class CinemaComponentsScreenshotTest {
    @get:Rule val rule = createComposeRule()
    @Test fun cinema() = capture(AppTheme.CINEMA, "cinema_v2_components")
    @Test fun azure() = capture(AppTheme.AZURE, "cinema_v2_components_azure")
    @Test fun midnight() = capture(AppTheme.MIDNIGHT, "cinema_v2_components_midnight")
    @Test fun daylight() = capture(AppTheme.DAYLIGHT, "cinema_v2_components_daylight")

    @Test @Config(qualifiers = "uk-w320dp-h1900dp-xhdpi")
    fun largeTextReducedEffects() = capture(AppTheme.CINEMA, "cinema_v2_components_200", 2f, true)

    private fun capture(theme: AppTheme, file: String, fontScale: Float = 1f, reduced: Boolean = false) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalReducedEffects provides reduced) {
                ZorikTheme(theme) { ComponentCatalogPreview() }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/$file.png")
    }

    @Test @Config(qualifiers = "uk-w960dp-h540dp-land-xhdpi")
    fun tvFocusedCardAndDisabledAction() {
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            TvPresentation(true, remember { TvInputRegistry() }) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                val colors = MaterialTheme.colorScheme
                Column(Modifier.fillMaxSize().background(colors.background).padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("ZORIK · Android TV", style = MaterialTheme.typography.displaySmall,
                        color = colors.onBackground)
                    Text("Видимий фокус · один target на дію · пульт і клавіатура",
                        style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        UaCard(Modifier.width(400.dp).focusRequester(focus), selected = true, onClick = {}) {
                            Text("Продовжити перегляд", style = MaterialTheme.typography.titleLarge)
                            Text("Картка у фокусі", modifier = Modifier.padding(top = 12.dp))
                        }
                        Column(Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PrimaryButton("Відкрити джерело", {}, Modifier.fillMaxWidth())
                            SecondaryButton("Недоступна дія", {}, Modifier.fillMaxWidth(), enabled = false)
                        }
                    }
                    UaListItem("Канал у вибраному", supportingText = "Виділення позначене кольором і галочкою",
                        modifier = Modifier.fillMaxWidth(), selected = true, onClick = {})
                }
            }
        } }
        rule.onRoot().captureRoboImage("src/test/screenshots/cinema_v2_tv_focus.png")
    }
}
