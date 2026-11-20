package com.uacastplayer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

private const val PANEL_TOP_TINT = 0.16f
private const val FEATURE_TOP_TINT = 0.10f
private const val PANEL_BOTTOM_TINT = 0.30f
private const val NAVIGATION_TINT = 0.14f
private const val INNER_RING_SCALE = 0.72f
private const val OUTER_RING_SCALE = 1.08f

/** Static depth; only featured cards may elevate, with elevation disabled by reduced effects. */
@Composable
fun Modifier.panelSurface(shape: Shape, featured: Boolean = false): Modifier {
    val palette = UaTheme.palette
    val top = panelTopColor(palette, featured)
    val bottom = panelBottomColor(palette)
    val fill = remember(top, bottom) { Brush.linearGradient(listOf(top, bottom)) }
    val edge = remember(palette.edgeHighlightNeutral, palette.hairline) {
        Brush.verticalGradient(listOf(palette.edgeHighlightNeutral, palette.hairline.copy(alpha = 0.12f)))
    }
    val base = if (featured && !LocalReducedEffects.current) {
        shadow(6.dp, shape, ambientColor = palette.shadowSoft, spotColor = palette.shadowSoft)
    } else this
    val panel = base.clip(shape).background(fill).border(1.dp, edge, shape)
    return if (!featured) panel else panel.drawWithCache {
        val center = Offset(size.width * 0.98f, -size.height * 0.08f)
        val radius = size.width * 0.56f
        val ring = Stroke(1.dp.toPx())
        onDrawBehind {
            drawCircle(palette.azure.copy(alpha = 0.05f), radius, center)
            drawCircle(palette.azure.copy(alpha = 0.12f), radius * INNER_RING_SCALE, center, style = ring)
            drawCircle(palette.azure.copy(alpha = 0.07f), radius * OUTER_RING_SCALE, center, style = ring)
        }
    }
}

internal fun panelTopColor(palette: UaPalette, featured: Boolean): Color =
    lerp(palette.surface1, if (featured) palette.azure else palette.surface2,
        if (featured) FEATURE_TOP_TINT else PANEL_TOP_TINT)

internal fun panelBottomColor(palette: UaPalette): Color = lerp(palette.surface1, palette.surface2, PANEL_BOTTOM_TINT)

internal fun navigationSelectionColor(palette: UaPalette): Color =
    lerp(palette.surface1, palette.azure, NAVIGATION_TINT)
