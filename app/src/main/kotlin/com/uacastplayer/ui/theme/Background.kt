package com.uacastplayer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.uacastplayer.R

// How strongly the wallpaper texture shows through under the theme-color gradient - low enough
// that card surfaces and text (drawn on top by every screen) keep the same contrast as the old
// flat gradient; this only textures the empty space around them.
private const val WALLPAPER_ALPHA = 0.85f
private const val OVERLAY_ALPHA = 0.3f

/**
 * The screen background: a decorative wallpaper image (soft ambient glow + grain, see
 * res/drawable-nodpi/app_wallpaper.jpg) with the theme's own gradient over it, from
 * [UaPalette.void] to [UaPalette.voidElevated] - breaks up an otherwise perfectly flat background
 * without introducing a new surface color, while still tinting to the active theme's hue. When
 * [UaPalette.vignette] is set (Cinema), a radial gradient is used instead for a softer, more
 * premium "spotlight" feel.
 */
@Composable
fun Modifier.appBackground(): Modifier {
    val palette = UaTheme.palette
    // Screen-root modifiers like this recompose along with whatever else is unstable in the
    // caller's composable (see block 2.4) - remembered so a Brush isn't reallocated on every one
    // of those recompositions when the palette itself hasn't actually changed.
    val overlayBrush = remember(palette.vignette, palette.voidElevated, palette.void) {
        val top = palette.voidElevated.copy(alpha = OVERLAY_ALPHA)
        val bottom = palette.void.copy(alpha = OVERLAY_ALPHA)
        if (palette.vignette) {
            Brush.radialGradient(colors = listOf(top, bottom))
        } else {
            Brush.verticalGradient(colors = listOf(bottom, top))
        }
    }
    // The wallpaper art is baked with Cinema's warm gold glow - BlendMode.Color keeps its
    // luminance (so the glow/vignette shape is untouched) but swaps the hue/saturation to the
    // active theme's own accent, so Azure gets a blue-tinted glow instead of a gold one that
    // would otherwise fight its cool palette.
    val wallpaperTint = remember(palette.azure) { ColorFilter.tint(palette.azure, BlendMode.Color) }
    return background(palette.void)
        .paint(
            painterResource(R.drawable.app_wallpaper),
            contentScale = ContentScale.Crop,
            alpha = WALLPAPER_ALPHA,
            colorFilter = wallpaperTint,
        )
        .background(overlayBrush)
}
