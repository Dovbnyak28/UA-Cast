package com.uacastplayer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private fun colorSchemeFor(palette: UaPalette): ColorScheme = darkColorScheme(
    primary = palette.azure,
    onPrimary = palette.accentOnFill,
    primaryContainer = palette.azure,
    onPrimaryContainer = palette.accentOnFill,
    secondary = palette.azure2,
    onSecondary = palette.void,
    background = palette.void,
    onBackground = palette.labelPrimary,
    surface = palette.surface1,
    onSurface = palette.labelPrimary,
    surfaceVariant = palette.surface2,
    onSurfaceVariant = palette.labelSecondary,
    surfaceContainer = palette.surface1,
    surfaceContainerHigh = palette.surface2,
    surfaceContainerHighest = palette.surface2,
    outline = palette.hairline,
    outlineVariant = palette.hairline,
    error = palette.routeRed,
    onError = Color.White,
)

/**
 * UA Cast Player is dark-only by design; [androidx.compose.foundation.isSystemInDarkTheme] is
 * intentionally not consulted. [theme] picks which [UaPalette] is exposed through [LocalUaPalette]
 * (and therefore [UaTheme.palette]) for the whole subtree - see docs/DESIGN_SYSTEM.md "Themes".
 */
@Composable
fun UaCastTheme(theme: AppTheme, content: @Composable () -> Unit) {
    // CINEMA temporarily maps to the Azure palette until CinemaUaPalette exists - keeps this block
    // self-contained and the app pixel-identical (see docs/DESIGN_SYSTEM.md "Themes").
    val palette = when (theme) {
        AppTheme.AZURE -> AzureUaPalette
        AppTheme.CINEMA -> AzureUaPalette
    }
    CompositionLocalProvider(LocalUaPalette provides palette) {
        MaterialTheme(
            colorScheme = colorSchemeFor(palette),
            typography = AppTypography,
            content = content,
        )
    }
}
