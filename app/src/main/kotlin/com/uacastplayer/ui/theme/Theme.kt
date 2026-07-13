package com.uacastplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** UA Cast Player is dark-only by design; [androidx.compose.foundation.isSystemInDarkTheme] is intentionally not consulted. */
private val UaCastColorScheme = darkColorScheme(
    primary = Azure,
    onPrimary = Color.White,
    primaryContainer = Azure,
    onPrimaryContainer = Color.White,
    secondary = Azure2,
    onSecondary = Void,
    background = Void,
    onBackground = LabelPrimary,
    surface = Surface1,
    onSurface = LabelPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = LabelSecondary,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface2,
    outline = Hairline,
    outlineVariant = Hairline,
    error = RouteRed,
    onError = Color.White,
)

@Composable
fun UaCastPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UaCastColorScheme,
        typography = AppTypography,
        content = content,
    )
}
