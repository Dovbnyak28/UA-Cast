package com.uacastplayer.icons

import kotlin.math.abs
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkTonePolicyTest {

    @Test
    fun `empty image has no tone`() {
        assertNull(ArtworkTonePolicy.of(IntArray(0)))
    }

    @Test
    fun `a solid red logo reads as red`() {
        val tone = requireTone(ArtworkTonePolicy.of(fill(0xFFCC0000.toInt(), count = 100)))
        assertHue(expected = 0f, actual = tone.hue)
    }

    @Test
    fun `a solid blue logo reads as blue`() {
        val tone = requireTone(ArtworkTonePolicy.of(fill(0xFF0033CC.toInt(), count = 100)))
        assertHue(expected = 220f, actual = tone.hue, tolerance = 15f)
    }

    /**
     * The reason hue is summed as a vector rather than averaged. Two reds either side of the 0/360
     * wrap average arithmetically to 180 - cyan, the complement of what is actually there.
     */
    @Test
    fun `reds either side of the hue wrap average to red, not to its complement`() {
        val justBelowWrap = 0xFFCC0033.toInt()
        val justAboveWrap = 0xFFCC3300.toInt()
        val pixels = fill(justBelowWrap, count = 50) + fill(justAboveWrap, count = 50)

        val tone = requireTone(ArtworkTonePolicy.of(pixels))

        assertHue(expected = 0f, actual = tone.hue, tolerance = 20f)
    }

    @Test
    fun `a white wordmark on transparency has no tone to borrow`() {
        val pixels = fill(0xFFFFFFFF.toInt(), count = 50) + fill(0x00000000, count = 50)
        assertNull(ArtworkTonePolicy.of(pixels))
    }

    @Test
    fun `a greyscale logo has no tone`() {
        assertNull(ArtworkTonePolicy.of(fill(0xFF808080.toInt(), count = 100)))
    }

    @Test
    fun `padding is not counted, so a mostly-transparent logo still reads its own colour`() {
        val pixels = fill(0xFF00AA22.toInt(), count = 10) + fill(0x1100AA22, count = 90)

        val tone = requireTone(ArtworkTonePolicy.of(pixels))

        assertHue(expected = 132f, actual = tone.hue, tolerance = 15f)
    }

    /** A handful of stray anti-aliasing pixels is not a logo colour. */
    @Test
    fun `too few coloured pixels claims no tone at all`() {
        val pixels = fill(0xFFCC0000.toInt(), count = 2) + fill(0xFFFFFFFF.toInt(), count = 98)
        assertNull(ArtworkTonePolicy.of(pixels))
    }

    @Test
    fun `a black outline does not drag the tone toward grey`() {
        val pixels = fill(0xFFCC0000.toInt(), count = 30) + fill(0xFF000000.toInt(), count = 70)

        val tone = requireTone(ArtworkTonePolicy.of(pixels))

        assertHue(expected = 0f, actual = tone.hue)
        assertTrue("expected saturation from the red alone, got ${tone.saturation}", tone.saturation > 0.9f)
    }

    private fun requireTone(tone: ArtworkTone?): ArtworkTone {
        assertNotNull("expected a tone, got none", tone)
        return tone!!
    }

    private fun fill(argb: Int, count: Int) = IntArray(count) { argb }

    /** Shortest distance around the hue circle, so 359 and 1 are 2 apart rather than 358. */
    private fun assertHue(expected: Float, actual: Float, tolerance: Float = 8f) {
        val delta = abs(((actual - expected + 540f) % 360f) - 180f)
        assertTrue("hue $actual is $delta away from $expected, tolerance $tolerance", delta <= tolerance)
    }
}
