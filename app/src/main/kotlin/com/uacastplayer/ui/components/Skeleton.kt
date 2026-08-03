package com.uacastplayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uacastplayer.ui.theme.GlideMs
import com.uacastplayer.ui.theme.UaTheme

/** Width of the travelling highlight, as a fraction of the block it sweeps across. */
private const val SHIMMER_BAND_FRACTION = 0.45f

/** How far above the base colour the highlight sits. Kept low on purpose - a skeleton that shines
 * competes with the content it is standing in for. */
private const val SHIMMER_HIGHLIGHT_ALPHA = 0.06f

val SkeletonRadius = 8.dp

/**
 * One shared clock for every skeleton block on a screen.
 *
 * Each block running its own [rememberInfiniteTransition] is the obvious way to write this and the
 * wrong one: independent clocks drift into a twinkle instead of a sweep, and the effect only reads
 * as "this screen is loading" when the highlight crosses the whole layout in step.
 */
@Composable
fun rememberShimmer(): State<Float> {
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(GlideMs, easing = LinearEasing)),
        label = "shimmerSweep",
    )
}

/**
 * A placeholder block the shape and size of content that has not arrived yet.
 *
 * Used instead of a spinner so the screen shows the *shape* of what is coming: when the real data
 * lands it fills an outline that is already on screen, rather than replacing a centred spinner with
 * a full layout - that jump is the thing this exists to remove.
 */
@Composable
fun SkeletonBlock(
    shimmer: State<Float>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(SkeletonRadius),
) {
    val base = UaTheme.palette.surface2
    val highlight = UaTheme.palette.labelPrimary.copy(alpha = SHIMMER_HIGHLIGHT_ALPHA)
    val progress by shimmer
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val band = size.width * SHIMMER_BAND_FRACTION
                // Starts fully off the left edge and ends fully off the right, so the highlight
                // enters and leaves rather than appearing and vanishing mid-block.
                val head = (size.width + band) * progress - band
                drawRect(color = base)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, highlight, Color.Transparent),
                        start = Offset(head, 0f),
                        end = Offset(head + band, 0f),
                    ),
                )
            },
    )
}

/** A skeleton stand-in for a line of text, [widthFraction] of the width available to it. */
@Composable
fun SkeletonTextLine(
    shimmer: State<Float>,
    widthFraction: Float,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    SkeletonBlock(
        shimmer = shimmer,
        modifier = modifier.fillMaxWidth(widthFraction).height(height),
        shape = RoundedCornerShape(height / 2),
    )
}

/** A square skeleton stand-in for an icon or badge. */
@Composable
fun SkeletonBadge(shimmer: State<Float>, size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    SkeletonBlock(shimmer = shimmer, modifier = modifier.size(size), shape = shape)
}
