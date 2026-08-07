package com.uacastplayer.ui.language

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.ui.theme.FlagColors
import com.uacastplayer.ui.theme.UaTheme

private val FlagWidth = 30.dp
private val FlagHeight = 20.dp
private val FlagCorner = 3.dp

// Union Jack proportions, as fractions of the flag's height. Named rather than inlined because
// each one is a band of the real flag, and the relationship between them (white always wider than
// the red it frames) is the whole reason the drawing reads as a Union Jack at this size.
private const val SALTIRE_WHITE = 0.28f
private const val SALTIRE_RED = 0.12f
private const val CROSS_WHITE = 0.36f
private const val CROSS_RED = 0.20f

// Spain's stripes are 1:2:1 rather than equal thirds.
private const val SPAIN_DIVISIONS = 4f
private const val SPAIN_YELLOW_UNITS = 2f

/**
 * The flag shown beside each entry in [LanguagePickerScreen].
 *
 * Drawn rather than shipped as assets or emoji, for one blunt reason: Android's system font has no
 * country-flag glyphs, so `🇺🇦` renders as the letters "UA" on most devices - the picker would look
 * broken on exactly the phones this app targets. Four `Canvas` calls cost nothing and look the same
 * everywhere.
 *
 * A flag is a country, not a language, and the mapping is a simplification the caller has chosen:
 * Spanish is not only Spain's and English is not only Britain's. It is here because a flag is
 * recognised faster than a word in a script you cannot read, which is the whole job of this screen.
 */
@Composable
internal fun LanguageFlag(language: AppLanguage, modifier: Modifier = Modifier) {
    val hairline = UaTheme.palette.hairline
    Canvas(
        modifier = modifier
            .size(width = FlagWidth, height = FlagHeight)
            .clip(RoundedCornerShape(FlagCorner))
            .border(1.dp, hairline, RoundedCornerShape(FlagCorner)),
    ) {
        when (language) {
            AppLanguage.UKRAINIAN -> horizontalBands(listOf(FlagColors.UaBlue, FlagColors.UaYellow))
            AppLanguage.RUSSIAN ->
                horizontalBands(listOf(FlagColors.RuWhite, FlagColors.RuBlue, FlagColors.RuRed))
            AppLanguage.SPANISH -> spanishBands()
            AppLanguage.ENGLISH -> unionJack()
        }
    }
}

/** Equal-height stripes, top to bottom - covers Ukraine (2) and Russia (3). Takes a list rather
 * than a vararg because `Color` is a value class, which Kotlin will not accept as a vararg type. */
private fun DrawScope.horizontalBands(colors: List<Color>) {
    val bandHeight = size.height / colors.size
    colors.forEachIndexed { index, color ->
        drawRect(
            color = color,
            topLeft = Offset(0f, bandHeight * index),
            size = Size(size.width, bandHeight),
        )
    }
}

/** Spain's stripes are 1:2:1, not equal thirds. */
private fun DrawScope.spanishBands() {
    val unit = size.height / SPAIN_DIVISIONS
    val yellowHeight = unit * SPAIN_YELLOW_UNITS
    drawRect(FlagColors.EsRed, Offset.Zero, Size(size.width, unit))
    drawRect(FlagColors.EsYellow, Offset(0f, unit), Size(size.width, yellowHeight))
    drawRect(FlagColors.EsRed, Offset(0f, unit + yellowHeight), Size(size.width, unit))
}

/**
 * A simplified Union Jack: the two saltires, then the two crosses over them. The real flag's
 * counterchanged (offset) red saltire is not reproduced - at 30x20dp it is a pixel or two wide and
 * reads as noise, so the diagonals are drawn centred.
 */
private fun DrawScope.unionJack() {
    val w = size.width
    val h = size.height
    drawRect(FlagColors.GbBlue)

    val topLeft = Offset(0f, 0f)
    val topRight = Offset(w, 0f)
    val bottomLeft = Offset(0f, h)
    val bottomRight = Offset(w, h)

    // White saltire, then the red one inside it.
    drawLine(FlagColors.GbWhite, topLeft, bottomRight, strokeWidth = h * SALTIRE_WHITE, cap = StrokeCap.Butt)
    drawLine(FlagColors.GbWhite, topRight, bottomLeft, strokeWidth = h * SALTIRE_WHITE, cap = StrokeCap.Butt)
    drawLine(FlagColors.GbRed, topLeft, bottomRight, strokeWidth = h * SALTIRE_RED, cap = StrokeCap.Butt)
    drawLine(FlagColors.GbRed, topRight, bottomLeft, strokeWidth = h * SALTIRE_RED, cap = StrokeCap.Butt)

    // White cross of St George, then the red one inside it.
    val whiteBar = h * CROSS_WHITE
    val redBar = h * CROSS_RED
    drawRect(FlagColors.GbWhite, Offset((w - whiteBar) / 2f, 0f), Size(whiteBar, h))
    drawRect(FlagColors.GbWhite, Offset(0f, (h - whiteBar) / 2f), Size(w, whiteBar))
    drawRect(FlagColors.GbRed, Offset((w - redBar) / 2f, 0f), Size(redBar, h))
    drawRect(FlagColors.GbRed, Offset(0f, (h - redBar) / 2f), Size(w, redBar))
}
