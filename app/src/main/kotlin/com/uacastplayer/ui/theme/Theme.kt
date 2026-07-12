package com.uacastplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** UA Cast Player is dark-only by design; [isSystemInDarkTheme] is intentionally not consulted. */
private val UaCastColorScheme = darkColorScheme(
    primary = UaCastPrimary,
    onPrimary = Color.White,
    secondary = UaCastCyan,
    onSecondary = UaCastBackground,
    background = UaCastBackground,
    onBackground = UaCastOnSurface,
    surface = UaCastSurface,
    onSurface = UaCastOnSurface,
    surfaceVariant = UaCastSurfaceVariant,
    onSurfaceVariant = UaCastOnSurfaceMuted,
    outline = UaCastBorder,
    error = UaCastError,
    onError = Color.White,
)

@Composable
fun UaCastPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UaCastColorScheme,
        typography = UaCastTypography,
        content = content,
    )
}
