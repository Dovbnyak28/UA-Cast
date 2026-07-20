package com.uacastplayer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

/**
 * The screen background. Most themes get a subtle top-to-bottom gradient from [UaPalette.void] to
 * [UaPalette.voidElevated] - breaks up an otherwise perfectly flat background without introducing
 * a new surface color. When [UaPalette.vignette] is set (Cinema), a radial gradient is used
 * instead for a softer, more premium "spotlight" feel.
 */
@Composable
fun Modifier.appBackground(): Modifier {
    val palette = UaTheme.palette
    // Screen-root modifiers like this recompose along with whatever else is unstable in the
    // caller's composable (see block 2.4) - remembered so a Brush isn't reallocated on every one
    // of those recompositions when the palette itself hasn't actually changed.
    val brush = remember(palette.vignette, palette.voidElevated, palette.void) {
        if (palette.vignette) {
            Brush.radialGradient(colors = listOf(palette.voidElevated, palette.void))
        } else {
            Brush.verticalGradient(colors = listOf(palette.void, palette.voidElevated))
        }
    }
    return background(brush)
}
