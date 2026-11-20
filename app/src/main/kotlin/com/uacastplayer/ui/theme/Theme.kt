package com.uacastplayer.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** User-selected chrome; video controls have their own consistently dark palette. */
@Composable
fun UaCastTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val palette = when (resolvedAppTheme(theme, isSystemInDarkTheme())) {
        AppTheme.AZURE -> AzureUaPalette
        AppTheme.CINEMA -> CinemaUaPalette
        AppTheme.MIDNIGHT -> MidnightUaPalette
        AppTheme.DAYLIGHT -> DaylightUaPalette
        AppTheme.SYSTEM -> MidnightUaPalette
    }
    val systemMotion = rememberSystemMotionEnabled()
    CompositionLocalProvider(LocalSystemMotionEnabled provides systemMotion) {
        UaPaletteTheme(palette, content)
    }
}

@Composable
internal fun UaPaletteTheme(palette: UaPalette, content: @Composable () -> Unit) {
    val colors = remember(palette) { semanticColorsFor(palette) }
    CompositionLocalProvider(LocalUaPalette provides palette, LocalUaSemanticColors provides colors) {
        MaterialTheme(
            colorScheme = colors.material,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Keep the same provider topology when the phone theme changes, preserving the native video view. */
@Composable
internal fun UaPlayerTheme(content: @Composable () -> Unit) {
    val palette = UaTheme.palette
    UaPaletteTheme(if (palette.isDark) palette else MidnightUaPalette, content)
}
