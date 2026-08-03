package com.uacastplayer.icons

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The colour a channel's logo is "about", as hue plus how strongly it carries that hue. */
data class ArtworkTone(val hue: Float, val saturation: Float)

/**
 * Picks one representative colour out of a channel logo, for tinting the surface the logo sits on.
 *
 * Pure pixel maths on an already-decoded, already-downscaled image, kept out of the UI so the two
 * decisions that make this either tasteful or awful can be tested directly:
 *
 * **Which pixels count.** A logo is mostly transparent padding, white text and a black outline, and
 * all three are colourless. Averaging everything therefore lands on grey no matter what the logo
 * actually looks like - so near-transparent, near-white, near-black and near-grey pixels are all
 * discarded, and only what is left votes.
 *
 * **How hue is averaged.** Hue is an angle, and the arithmetic mean of 350 and 10 is 180 - the
 * complementary colour. A red logo averaged naively comes out cyan. Hues are therefore summed as
 * unit vectors and the mean taken with atan2, which is the only way that gives red for red.
 *
 * Returns null when too few pixels qualify: a white-on-transparent wordmark genuinely has no tone,
 * and inventing one from a handful of anti-aliasing pixels is worse than showing none.
 */
object ArtworkTonePolicy {

    /** Below this the pixel is padding, not artwork. */
    const val MIN_ALPHA = 128

    private const val MIN_SATURATION = 0.25f
    private const val MIN_VALUE = 0.18f
    private const val MAX_VALUE = 0.98f

    /** Share of the image that must be coloured before a tone is claimed at all. */
    private const val MIN_COLOURED_FRACTION = 0.04f

    private const val FULL_CIRCLE = 360f
    private const val HUE_SECTOR = 60f
    private const val HUE_SECTORS = 6f

    // Where each sector starts, in sector units - the standard RGB-to-hue piecewise definition.
    private const val GREEN_SECTOR_OFFSET = 2f
    private const val BLUE_SECTOR_OFFSET = 4f

    // ARGB unpacking.
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val CHANNEL_MASK = 0xFF
    private const val CHANNEL_MAX = 255f

    @Suppress("ReturnCount")
    fun of(pixels: IntArray): ArtworkTone? {
        if (pixels.isEmpty()) return null

        var hueX = 0f
        var hueY = 0f
        var saturationSum = 0f
        var counted = 0

        for (pixel in pixels) {
            val hsv = colouredHsvOf(pixel) ?: continue

            val radians = Math.toRadians(hsv.hue.toDouble())
            hueX += cos(radians).toFloat() * hsv.saturation
            hueY += sin(radians).toFloat() * hsv.saturation
            saturationSum += hsv.saturation
            counted++
        }

        if (counted < max(1, (pixels.size * MIN_COLOURED_FRACTION).toInt())) return null
        if (hueX == 0f && hueY == 0f) return null

        val meanHue = (Math.toDegrees(atan2(hueY.toDouble(), hueX.toDouble())).toFloat() + FULL_CIRCLE) % FULL_CIRCLE
        return ArtworkTone(hue = meanHue, saturation = saturationSum / counted)
    }

    private data class Hsv(val hue: Float, val saturation: Float)

    /** Null for any pixel too transparent, too dark, too bright or too grey to have an opinion. */
    @Suppress("ReturnCount")
    private fun colouredHsvOf(pixel: Int): Hsv? {
        if (((pixel ushr ALPHA_SHIFT) and CHANNEL_MASK) < MIN_ALPHA) return null

        val r = ((pixel shr RED_SHIFT) and CHANNEL_MASK) / CHANNEL_MAX
        val g = ((pixel shr GREEN_SHIFT) and CHANNEL_MASK) / CHANNEL_MAX
        val b = (pixel and CHANNEL_MASK) / CHANNEL_MAX

        val maxComponent = max(r, max(g, b))
        val minComponent = min(r, min(g, b))
        if (maxComponent < MIN_VALUE || maxComponent > MAX_VALUE) return null

        val delta = maxComponent - minComponent
        val saturation = if (maxComponent == 0f) 0f else delta / maxComponent
        if (saturation < MIN_SATURATION) return null

        val hue = when (maxComponent) {
            r -> HUE_SECTOR * (((g - b) / delta) % HUE_SECTORS)
            g -> HUE_SECTOR * (((b - r) / delta) + GREEN_SECTOR_OFFSET)
            else -> HUE_SECTOR * (((r - g) / delta) + BLUE_SECTOR_OFFSET)
        }
        return Hsv(hue = (hue + FULL_CIRCLE) % FULL_CIRCLE, saturation = saturation)
    }
}
