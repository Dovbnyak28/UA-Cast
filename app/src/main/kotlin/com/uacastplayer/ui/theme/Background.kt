package com.uacastplayer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Cached colour fields, with no wallpaper decode, blur, offscreen layer or animation clock. */
@Composable
fun Modifier.appBackground(plain: Boolean = false): Modifier {
    val palette = UaTheme.palette
    val flat = plain || (!palette.wallpaperTexture && palette.isDark)
    val fill = remember(palette.voidElevated, palette.void) {
        Brush.verticalGradient(listOf(palette.voidElevated, palette.void))
    }
    val base = if (flat) background(palette.void) else background(fill)
    return if (flat || LocalReducedEffects.current || !palette.wallpaperTexture) base else {
        base.drawWithCache {
            val ambient = Brush.radialGradient(
                listOf(palette.azure.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(if (palette.vignette) size.width * 0.5f else size.width, 0f),
                radius = size.maxDimension * 0.7f,
            )
            onDrawBehind { drawRect(ambient) }
        }
    }
}
