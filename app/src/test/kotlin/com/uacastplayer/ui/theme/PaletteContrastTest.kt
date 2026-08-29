package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN_ICON_CONTRAST = 3f
private const val MIN_TEXT_CONTRAST = 4.5f
private const val SRGB_THRESHOLD = 0.03928f
private const val SRGB_DIVISOR = 12.92f
private const val SRGB_OFFSET = 0.055f
private const val SRGB_SCALE = 1.055f
private const val WCAG_OFFSET = 0.05f
private const val RED_WEIGHT = 0.2126f
private const val GREEN_WEIGHT = 0.7152f
private const val BLUE_WEIGHT = 0.0722f

class PaletteContrastTest {

    @Test
    fun `accent icon color contrasts with every gradient endpoint`() {
        palettes().forEach { (name, palette) ->
            assertTrue(
                "$name accentOnFill vs gradient top",
                contrastRatio(palette.accentOnFill, palette.accentGradientTop) >= MIN_ICON_CONTRAST,
            )
            assertTrue(
                "$name accentOnFill vs gradient bottom",
                contrastRatio(palette.accentOnFill, palette.accentGradientBottom) >= MIN_ICON_CONTRAST,
            )
        }
    }

    @Test
    fun `primary text remains readable on dark surfaces`() {
        palettes().forEach { (name, palette) ->
            assertTrue(
                "$name primary text vs void",
                contrastRatio(palette.labelPrimary, palette.void) >= MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "$name primary text vs surface1",
                contrastRatio(palette.labelPrimary, palette.surface1) >= MIN_TEXT_CONTRAST,
            )
        }
    }

    private fun palettes(): List<Pair<String, UaPalette>> = listOf(
        "azure" to AzureUaPalette,
        "cinema" to CinemaUaPalette,
        "midnight" to MidnightUaPalette,
    )
}

private fun contrastRatio(first: Color, second: Color): Float {
    val firstLuminance = relativeLuminance(first)
    val secondLuminance = relativeLuminance(second)
    val lighter = maxOf(firstLuminance, secondLuminance)
    val darker = minOf(firstLuminance, secondLuminance)
    return (lighter + WCAG_OFFSET) / (darker + WCAG_OFFSET)
}

private fun relativeLuminance(color: Color): Float {
    fun linear(channel: Float): Float =
        if (channel <= SRGB_THRESHOLD) {
            channel / SRGB_DIVISOR
        } else {
            ((channel + SRGB_OFFSET) / SRGB_SCALE).let { it * it * it }
        }

    return RED_WEIGHT * linear(color.red) +
        GREEN_WEIGHT * linear(color.green) +
        BLUE_WEIGHT * linear(color.blue)
}
