package app.zorik.player.ui.guidedtour

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.zorik.player.guidedtour.IntroPage
import app.zorik.player.ui.theme.CategoryTones
import app.zorik.player.ui.theme.ChannelAvatarColors
import app.zorik.player.ui.theme.UaPalette
import app.zorik.player.ui.theme.UaTheme

/*
 * The intro's pictures, drawn rather than shipped as images: they follow the theme (light and dark,
 * every accent) for free, cost no APK bytes and no decode, and stay sharp at any size. Every
 * coordinate is a fraction of the square they are drawn in, so the same drawing serves a small
 * landscape phone and a tablet.
 */

private const val GLOW_ALPHA = 0.30f
private const val BAR_ALPHA = 0.45f
private const val FAINT_BAR_ALPHA = 0.22f
private const val PLAY_DISC_ALPHA = 0.92f

/** A decorative picture for [page]. No semantics: the page's title and text say what it shows. */
@Composable
internal fun IntroIllustration(page: IntroPage, modifier: Modifier = Modifier) {
    val palette = UaTheme.palette
    val glow = CategoryTones.color(page.hue(), palette.isDark)
    Canvas(modifier = modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(glow.copy(alpha = GLOW_ALPHA), Color.Transparent),
                center = center,
                radius = size.minDimension / 2f,
            ),
        )
        when (page) {
            IntroPage.PLAYLIST -> drawPlaylist(palette)
            IntroPage.WATCH -> drawWatch(palette)
            IntroPage.CAST -> drawCast(palette)
        }
    }
}

private fun IntroPage.hue(): CategoryTones.Hue = when (this) {
    IntroPage.PLAYLIST -> CategoryTones.Hue.Sky
    IntroPage.WATCH -> CategoryTones.Hue.Violet
    IntroPage.CAST -> CategoryTones.Hue.Teal
}

private fun DrawScope.u(fraction: Float): Float = size.minDimension * fraction

/** A list card with three channel rows, and an accent "+" badge on its corner. */
private fun DrawScope.drawPlaylist(palette: UaPalette) {
    val cardLeft = u(0.17f)
    val cardTop = u(0.22f)
    val cardSize = Size(u(0.62f), u(0.58f))
    drawRoundRect(palette.surface2, Offset(cardLeft, cardTop), cardSize, CornerRadius(u(0.07f)))
    drawRoundRect(
        palette.hairline, Offset(cardLeft, cardTop), cardSize, CornerRadius(u(0.07f)),
        style = Stroke(width = u(0.006f)),
    )
    val tile = u(0.11f)
    repeat(3) { row ->
        val top = cardTop + u(0.07f) + row * u(0.16f)
        val left = cardLeft + u(0.06f)
        drawRoundRect(ChannelAvatarColors.brushAt(row * 3), Offset(left, top), Size(tile, tile), CornerRadius(u(0.03f)))
        val barLeft = left + tile + u(0.04f)
        drawRoundRect(
            palette.labelPrimary.copy(alpha = BAR_ALPHA), Offset(barLeft, top + u(0.015f)),
            Size(u(0.28f - row * 0.04f), u(0.032f)), CornerRadius(u(0.016f)),
        )
        drawRoundRect(
            palette.labelSecondary.copy(alpha = FAINT_BAR_ALPHA), Offset(barLeft, top + u(0.065f)),
            Size(u(0.18f), u(0.026f)), CornerRadius(u(0.013f)),
        )
    }
    val badge = Offset(u(0.77f), u(0.25f))
    drawCircle(palette.accentGradient, radius = u(0.1f), center = badge)
    val arm = u(0.045f)
    val stroke = u(0.022f)
    val plus = palette.accentOnFill
    drawLine(plus, badge.copy(x = badge.x - arm), badge.copy(x = badge.x + arm), stroke, StrokeCap.Round)
    drawLine(plus, badge.copy(y = badge.y - arm), badge.copy(y = badge.y + arm), stroke, StrokeCap.Round)
}

/** A video frame with a play disc and a live dot, over a strip of channel tiles with one selected. */
private fun DrawScope.drawWatch(palette: UaPalette) {
    val frameTop = Offset(u(0.1f), u(0.2f))
    val frameSize = Size(u(0.8f), u(0.45f))
    drawRoundRect(ChannelAvatarColors.brushAt(4), frameTop, frameSize, CornerRadius(u(0.06f)))
    val centre = Offset(frameTop.x + frameSize.width / 2f, frameTop.y + frameSize.height / 2f)
    drawCircle(ChannelAvatarColors.initials.copy(alpha = PLAY_DISC_ALPHA), radius = u(0.09f), center = centre)
    val triangle = Path().apply {
        moveTo(centre.x - u(0.03f), centre.y - u(0.045f))
        lineTo(centre.x + u(0.05f), centre.y)
        lineTo(centre.x - u(0.03f), centre.y + u(0.045f))
        close()
    }
    drawPath(triangle, ChannelAvatarColors.brushAt(4))
    drawRoundRect(
        palette.routeRed, Offset(frameTop.x + u(0.05f), frameTop.y + u(0.05f)), Size(u(0.13f), u(0.05f)),
        CornerRadius(u(0.025f)),
    )
    drawCircle(
        ChannelAvatarColors.initials, radius = u(0.012f),
        center = Offset(frameTop.x + u(0.08f), frameTop.y + u(0.075f)),
    )
    val tile = u(0.15f)
    val gap = u(0.067f)
    repeat(4) { index ->
        val left = u(0.1f) + index * (tile + gap)
        val top = u(0.71f)
        drawRoundRect(
            ChannelAvatarColors.brushAt(index * 2 + 1), Offset(left, top), Size(tile, tile), CornerRadius(u(0.04f)),
        )
        if (index == 1) {
            val ring = u(0.018f)
            drawRoundRect(
                palette.azure, Offset(left - ring, top - ring), Size(tile + ring * 2, tile + ring * 2),
                CornerRadius(u(0.055f)), style = Stroke(width = u(0.012f)),
            )
        }
    }
}

/** A phone sending waves to a television. */
private fun DrawScope.drawCast(palette: UaPalette) {
    val tvTop = Offset(u(0.3f), u(0.2f))
    val tvSize = Size(u(0.6f), u(0.38f))
    drawRoundRect(palette.surface2, tvTop, tvSize, CornerRadius(u(0.05f)))
    val inset = u(0.03f)
    drawRoundRect(
        ChannelAvatarColors.brushAt(1), Offset(tvTop.x + inset, tvTop.y + inset),
        Size(tvSize.width - inset * 2, tvSize.height - inset * 2), CornerRadius(u(0.03f)),
    )
    val standX = tvTop.x + tvSize.width / 2f
    val standTop = tvTop.y + tvSize.height
    drawLine(palette.labelSecondary, Offset(standX, standTop), Offset(standX, standTop + u(0.06f)), u(0.02f))
    drawLine(
        palette.labelSecondary, Offset(standX - u(0.1f), standTop + u(0.07f)),
        Offset(standX + u(0.1f), standTop + u(0.07f)), u(0.02f), StrokeCap.Round,
    )

    val phoneTop = Offset(u(0.1f), u(0.47f))
    val phoneSize = Size(u(0.19f), u(0.34f))
    drawRoundRect(palette.surface2, phoneTop, phoneSize, CornerRadius(u(0.04f)))
    drawRoundRect(
        palette.labelSecondary, phoneTop, phoneSize, CornerRadius(u(0.04f)), style = Stroke(width = u(0.01f)),
    )
    drawRoundRect(
        ChannelAvatarColors.brushAt(1), Offset(phoneTop.x + u(0.02f), phoneTop.y + u(0.04f)),
        Size(phoneSize.width - u(0.04f), u(0.12f)), CornerRadius(u(0.02f)),
    )

    // Three widening arcs from the phone's top corner towards the screen.
    val origin = Offset(phoneTop.x + phoneSize.width - u(0.02f), phoneTop.y + u(0.02f))
    listOf(0.08f, 0.14f, 0.2f).forEachIndexed { index, radius ->
        val r = u(radius)
        drawArc(
            color = palette.azure.copy(alpha = 1f - index * 0.25f),
            startAngle = -90f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(origin.x - r, origin.y - r), size = Size(r * 2, r * 2),
            style = Stroke(width = u(0.022f), cap = StrokeCap.Round),
        )
    }
}
