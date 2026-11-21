package com.uacastplayer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

private const val TONAL_ACCENT_FRACTION = 0.14f
private const val FIXED_LIGHT_FRACTION = 0.25f
private const val FIXED_DIM_FRACTION = 0.40f
private const val CONTENT_LUMINANCE_THRESHOLD = 0.179f
private const val MID_TONE_FRACTION = 0.5f

/** Theme-derived roles also work for palette.copy(), including the light and SYSTEM themes. */
internal fun colorSchemeFor(palette: UaPalette): ColorScheme {
    val base = if (palette.isDark) darkColorScheme() else lightColorScheme()
    val tertiary = palette.labelSecondary
    return base.copy(
        primary = palette.azure, onPrimary = palette.accentOnFill,
        primaryContainer = tonalContainer(palette, palette.azure), onPrimaryContainer = palette.labelPrimary,
        inversePrimary = if (palette.isDark) darken(palette.azure, MID_TONE_FRACTION)
            else lighten(palette.azure, MID_TONE_FRACTION),
        secondary = palette.azure2, onSecondary = statusFillContent(palette.azure2),
        secondaryContainer = tonalContainer(palette, palette.azure2), onSecondaryContainer = palette.labelPrimary,
        tertiary = tertiary, onTertiary = statusFillContent(tertiary),
        tertiaryContainer = tonalContainer(palette, tertiary), onTertiaryContainer = palette.labelPrimary,
        background = palette.void, onBackground = palette.labelPrimary,
        surface = palette.surface1, onSurface = palette.labelPrimary,
        surfaceVariant = palette.surface2, onSurfaceVariant = palette.labelSecondary,
        surfaceDim = if (palette.isDark) palette.void else palette.surface2,
        surfaceBright = if (palette.isDark) palette.surface2 else palette.surface1,
        surfaceContainerLowest = palette.void,
        surfaceContainerLow = lerp(palette.void, palette.surface1, MID_TONE_FRACTION),
        surfaceContainer = palette.surface1,
        surfaceContainerHigh = lerp(palette.surface1, palette.surface2, MID_TONE_FRACTION),
        surfaceContainerHighest = palette.surface2,
        surfaceTint = palette.azure,
        inverseSurface = palette.labelPrimary, inverseOnSurface = palette.void,
        outline = palette.labelTertiary, outlineVariant = palette.hairline.compositeOver(palette.surface1),
        error = palette.routeRed, onError = statusFillContent(palette.routeRed),
        errorContainer = tonalContainer(palette, palette.routeRed), onErrorContainer = palette.labelPrimary,
        scrim = Color.Black,
        primaryFixed = fixedTone(palette.azure, false), primaryFixedDim = fixedTone(palette.azure, true),
        onPrimaryFixed = Color.Black, onPrimaryFixedVariant = Color.Black,
        secondaryFixed = fixedTone(palette.azure2, false), secondaryFixedDim = fixedTone(palette.azure2, true),
        onSecondaryFixed = Color.Black, onSecondaryFixedVariant = Color.Black,
        tertiaryFixed = fixedTone(tertiary, false), tertiaryFixedDim = fixedTone(tertiary, true),
        onTertiaryFixed = Color.Black, onTertiaryFixedVariant = Color.Black,
    )
}

internal fun tonalContainer(palette: UaPalette, accent: Color): Color =
    lerp(palette.surface1, accent, TONAL_ACCENT_FRACTION)

private fun fixedTone(accent: Color, dim: Boolean): Color =
    lerp(Color.White, accent, if (dim) FIXED_DIM_FRACTION else FIXED_LIGHT_FRACTION)

private fun statusFillContent(fill: Color): Color =
    if (fill.luminance() > CONTENT_LUMINANCE_THRESHOLD) Color.Black else Color.White

@Immutable
data class StatusColors(val accent: Color, val container: Color, val onContainer: Color)

@Immutable
data class UaSemanticColors(
    val material: ColorScheme,
    val success: StatusColors,
    val warning: StatusColors,
    val danger: StatusColors,
    val focusRing: Color,
    val focusSeparator: Color,
)

internal fun semanticColorsFor(palette: UaPalette): UaSemanticColors {
    fun status(accent: Color) = StatusColors(accent, tonalContainer(palette, accent), palette.labelPrimary)
    return UaSemanticColors(colorSchemeFor(palette), status(palette.routeGreen), status(palette.routeAmber),
        status(palette.routeRed), palette.azure, palette.void)
}

internal val LocalUaSemanticColors = staticCompositionLocalOf { semanticColorsFor(AzureUaPalette) }
