package app.zorik.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyAndStatusContractTest {
    @Test fun everyMaterialRoleHasAnOfflineFamilyAndExplicitReadableLineHeight() {
        val t = AppTypography
        val styles = listOf(t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium,
            t.headlineSmall, t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall)
        styles.forEach {
            assertEquals(FontFamily.SansSerif, it.fontFamily)
            assertTrue("Line height must leave room for Cyrillic glyphs", it.lineHeight.value >= it.fontSize.value)
        }
    }

    @Test fun statusContentAndFocusSeparationPassContrastInEveryPalette() {
        for (palette in listOf(AzureUaPalette, CinemaUaPalette, MidnightUaPalette, DaylightUaPalette)) {
            val colors = semanticColorsFor(palette)
            for (status in listOf(colors.success, colors.warning, colors.danger)) {
                assertTrue("Unreadable status", contrast(status.onContainer, status.container) >= 4.5f)
            }
            assertTrue(contrast(colors.focusRing, palette.surface2) >= 3f)
            assertTrue("Focus ring must also stand out on a filled action",
                contrast(colors.focusSeparator, palette.azure) >= 3f)
        }
    }

    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
}
