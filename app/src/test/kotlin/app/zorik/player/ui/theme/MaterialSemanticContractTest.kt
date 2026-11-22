package app.zorik.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The Material components must use the active UA palette, including every tonal surface. */
@RunWith(Parameterized::class)
class MaterialSemanticContractTest(private val name: String, private val palette: UaPalette) {
    @Test fun tonalPrimaryContainerIsDistinctFromTheFilledAction() {
        val scheme = colorSchemeFor(palette)
        assertTrue("$name has no distinction between tonal selection and a primary action",
            scheme.primaryContainer != scheme.primary)
    }

    @Test fun surfaceTintUsesTheActiveBrandInsteadOfTheMaterialDefault() {
        assertEquals("$name surface tint", palette.azure, colorSchemeFor(palette).surfaceTint)
    }

    @Test fun interactiveOutlineIsOpaqueAndVisibleOnBothContainerSurfaces() {
        val outline = colorSchemeFor(palette).outline
        assertEquals("$name outline opacity", 1f, outline.alpha, 0f)
        for (surface in listOf(palette.surface1, palette.surface2)) {
            assertTrue("$name outline is below 3:1 against $surface", contrast(outline, surface) >= 3f)
        }
    }

    @Test fun everyFilledAndTonalContentPairRemainsReadable() {
        val scheme = colorSchemeFor(palette)
        val pairs = listOf(
            "primary" to (scheme.onPrimary to scheme.primary),
            "primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
            "secondary" to (scheme.onSecondary to scheme.secondary),
            "secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
            "tertiary" to (scheme.onTertiary to scheme.tertiary),
            "tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
            "error" to (scheme.onError to scheme.error),
            "errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
            "inverseSurface" to (scheme.inverseOnSurface to scheme.inverseSurface),
        )
        for ((role, pair) in pairs) {
            assertTrue("$name $role content is below 4.5:1", contrast(pair.first, pair.second) >= 4.5f)
        }
    }

    @Test fun surfaceElevationAnchorsFollowTheActivePalette() {
        val scheme = colorSchemeFor(palette)
        assertEquals("$name lowest", palette.void, scheme.surfaceContainerLowest)
        assertEquals("$name regular", palette.surface1, scheme.surfaceContainer)
        assertEquals("$name highest", palette.surface2, scheme.surfaceContainerHighest)
        assertBetween("$name low", scheme.surfaceContainerLow, palette.void, palette.surface1)
        assertBetween("$name high", scheme.surfaceContainerHigh, palette.surface1, palette.surface2)
    }

    private fun assertBetween(role: String, actual: Color, first: Color, second: Color) {
        for ((channel, endpoints) in listOf(
            actual.red to (first.red to second.red),
            actual.green to (first.green to second.green),
            actual.blue to (first.blue to second.blue),
        )) {
            assertTrue("$role inherited a surface outside its palette", channel in
                minOf(endpoints.first, endpoints.second)..maxOf(endpoints.first, endpoints.second))
        }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val foregroundLuminance = foreground.compositeOver(background).luminance()
        val backgroundLuminance = background.luminance()
        return (maxOf(foregroundLuminance, backgroundLuminance) + 0.05f) /
            (minOf(foregroundLuminance, backgroundLuminance) + 0.05f)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun palettes(): List<Array<Any>> = listOf(
            arrayOf("Azure", AzureUaPalette),
            arrayOf("Cinema", CinemaUaPalette),
            arrayOf("Midnight", MidnightUaPalette),
            arrayOf("Daylight", DaylightUaPalette),
        )
    }
}
