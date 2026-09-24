package com.uacastplayer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.uacastplayer.R

// Keep the dark texture opaque enough to retain its own black levels, then mute it with the
// theme-coloured overlay below. Lowering the image alpha itself reveals a much lighter intermediate
// layer on some renderers, which is the opposite of the subdued form background we need.
private const val WALLPAPER_ALPHA = 0.85f
private const val OVERLAY_ALPHA = 0.82f
private const val AMBIENT_GLOW_ALPHA = 0.14f
private const val AMBIENT_GLOW_SECONDARY_ALPHA = 0.09f

/**
 * The screen background: a decorative wallpaper image (soft ambient glow + grain, see
 * res/drawable-nodpi/app_wallpaper.jpg) with the theme's own gradient over it, from
 * [UaPalette.void] to [UaPalette.voidElevated] - breaks up an otherwise perfectly flat background
 * without introducing a new surface color, while still tinting to the active theme's hue. Two
 * static, low-opacity accent blooms add atmosphere without an animation clock or animation-driven
 * redraws. When [UaPalette.vignette] is set (Cinema), a radial gradient is used instead for a
 * softer, more premium "spotlight" feel.
 */
@Composable
fun Modifier.appBackground(plain: Boolean = false): Modifier {
    val palette = UaTheme.palette
    // Forms/settings keep the theme's colors without competing wallpaper. Return before allocating
    // brushes or loading artwork; Midnight uses the same flat path for all destinations.
    if (plain || !palette.wallpaperTexture) return background(palette.void)
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
    // luminance (so the glow/vignette shape is untouched) but borrows the theme's secondary accent
    // hue. Azure therefore gets a mint-blue wash and Cinema a soft coral one; neither inherits
    // the wallpaper's baked-in gold cast.
    val wallpaperTint = remember(palette.accentGradientBottom) {
        ColorFilter.tint(palette.accentGradientBottom, BlendMode.Color)
    }
    return background(palette.void)
        .paint(
            painterResource(R.drawable.app_wallpaper),
            contentScale = ContentScale.Crop,
            alpha = WALLPAPER_ALPHA,
            colorFilter = wallpaperTint,
        )
        .background(overlayBrush)
        .drawWithCache {
            val firstGlow = Brush.radialGradient(
                colors = listOf(
                    palette.azure.copy(alpha = AMBIENT_GLOW_ALPHA),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.08f, size.height * 0.03f),
                radius = size.maxDimension * 0.9f,
            )
            val secondGlow = Brush.radialGradient(
                colors = listOf(
                    palette.azure2.copy(alpha = AMBIENT_GLOW_SECONDARY_ALPHA),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.96f, size.height * 0.26f),
                radius = size.maxDimension * 0.64f,
            )
            onDrawWithContent {
                drawContent()
                drawRect(firstGlow)
                drawRect(secondGlow)
            }
        }
}
