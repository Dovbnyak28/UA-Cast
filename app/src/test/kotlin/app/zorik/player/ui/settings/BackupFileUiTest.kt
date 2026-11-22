package app.zorik.player.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.zorik.player.backup.BackupImportSummary
import app.zorik.player.premium.Entitlements
import app.zorik.player.premium.FeatureManager
import app.zorik.player.premium.License
import app.zorik.player.premium.LicenseTier
import app.zorik.player.premium.PremiumSectionState
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.premium.LocalFeatureGate
import app.zorik.player.ui.premium.rememberFeatureGate
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import app.zorik.player.ui.theme.UaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class BackupFileUiTest {
    @get:Rule val rule = createComposeRule()
    private var exports = 0
    private var imports = 0

    private fun render(entitlements: Entitlements) {
        val manager = FeatureManager(MutableStateFlow(entitlements))
        rule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                val section = PremiumSectionState(entitlements, emptyList(), {}, {})
                val surfaces = rememberFeatureGate(manager, section)
                CompositionLocalProvider(LocalFeatureGate provides surfaces.gate) {
                    Box(Modifier.width(288.dp).background(UaTheme.palette.void)) {
                        DataSettingsSection(onImportBackup = { imports++ }, onShowExportWarning = { exports++ })
                    }
                }
            }
        }
    }

    @Test fun premiumActionsAreReadableAndClickableOnNarrowPhone() {
        render(Entitlements.of(License(LicenseTier.LIFETIME), 0))
        rule.onNodeWithText("Зберегти у файл").assertExists().performClick()
        rule.onNodeWithText("Відновити з файлу").assertExists().performClick()
        assertEquals(1, exports)
        assertEquals(1, imports)
        rule.onRoot().captureRoboImage("src/test/screenshots/backup_file_narrow.png")
    }

    @Test fun liteCannotRunFileActions() {
        render(Entitlements.FREE)
        rule.onNodeWithText("Зберегти у файл").performClick()
        assertEquals(0, exports)
        assertEquals(0, imports)
    }

    @Test fun invalidFileShowsNoChangesMessageInsteadOfSuccess() {
        rule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                BackupImportSummaryBanner(BackupImportSummary(0, 0, fileRejected = true), {})
            }
        }
        rule.onNodeWithText("Поточні плейлисти, улюблені канали та налаштування не змінено.", substring = true)
            .assertExists()
        rule.onNodeWithText("Імпортовано:", substring = true).assertDoesNotExist()
    }
}
