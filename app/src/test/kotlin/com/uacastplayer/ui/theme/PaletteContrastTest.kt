package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN_ICON_CONTRAST = 3f
private const val MIN_TEXT_CONTRAST = 4.5f
private const val SRGB_THRESHOLD = 0.04045f
private const val SRGB_DIVISOR = 12.92f
private const val SRGB_OFFSET = 0.055f
private const val SRGB_SCALE = 1.055f
private const val WCAG_OFFSET = 0.05f
private const val RED_WEIGHT = 0.2126f
private const val GREEN_WEIGHT = 0.7152f
private const val BLUE_WEIGHT = 0.0722f

class PaletteContrastTest {

    @Test
    fun `reference contrast pairs and translucent text use WCAG luminance`() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.001f)
        assertEquals(1f, contrastRatio(Color.Black, Color.Black), 0.001f)
        assertEquals(4.478f, contrastRatio(Color(0xFF777777), Color.White), 0.002f)
        // Compose stores sRGB alpha in 8 bits: 0.5 rounds to 128/255.
        assertEquals(5.317f, contrastRatio(Color.White.copy(alpha = 0.5f), Color.Black), 0.002f)
    }

    @Test
    fun `selected chip and filled button text are readable in all themes`() {
        palettes().forEach { (name, palette) ->
            listOf(palette.azure, palette.accentGradientTop, palette.accentGradientBottom).forEach { fill ->
                assertTrue("$name filled text", contrastRatio(palette.accentOnFill, fill) >= MIN_TEXT_CONTRAST)
            }
        }
    }

    @Test
    fun `secondary informational text is readable on sheet surfaces`() {
        palettes().forEach { (name, palette) ->
            assertTrue(
                "$name secondary text",
                contrastRatio(palette.labelSecondary, palette.surface2) >= MIN_TEXT_CONTRAST,
            )
        }
    }

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
    val firstLuminance = relativeLuminance(first.compositeOver(second))
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
            ((channel + SRGB_OFFSET) / SRGB_SCALE).pow(2.4f)
        }

    return RED_WEIGHT * linear(color.red) +
        GREEN_WEIGHT * linear(color.green) +
        BLUE_WEIGHT * linear(color.blue)
}
