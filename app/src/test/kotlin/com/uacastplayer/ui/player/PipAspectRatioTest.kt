package com.uacastplayer.ui.player

import android.util.Rational
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [PipController.aspectRatioFor] used to be the constant `Rational(16, 9)`, which is wrong for every
 * channel that isn't 16:9 - and a Ukrainian IPTV playlist carries plenty of 4:3 SD ones, where the
 * picture ended up letterboxed inside an already-small floating window.
 *
 * Robolectric only for [Rational], which is a framework class with real arithmetic in it (`toFloat`,
 * `equals` by reduced value) rather than something worth faking.
 */
@RunWith(RobolectricTestRunner::class)
class PipAspectRatioTest {

    private fun ratioOf(width: Int, height: Int, par: Float = 1f): Float =
        PipController.aspectRatioFor(width, height, par).toFloat()

    @Test
    fun `square-pixel HD keeps its exact dimensions`() {
        assertEquals(Rational(1920, 1080), PipController.aspectRatioFor(1920, 1080))
    }

    @Test
    fun `4 by 3 SD is not reported as 16 by 9`() {
        val ratio = ratioOf(640, 480)
        assertEquals(4f / 3f, ratio, 0.001f)
        assertTrue("must not fall back to 16:9", ratio < 16f / 9f)
    }

    /**
     * The case sample dimensions alone cannot express: 720x576 PAL samples with a 16:15 pixel
     * aspect are a 4:3 picture, not the 1.25:1 the raw numbers suggest.
     */
    @Test
    fun `anamorphic PAL SD uses the pixel aspect ratio, not the sample dimensions`() {
        val fromSamplesAlone = 720f / 576f
        val ratio = ratioOf(720, 576, par = 16f / 15f)

        assertEquals(4f / 3f, ratio, 0.01f)
        assertTrue("pixel aspect must actually be applied", ratio > fromSamplesAlone)
    }

    @Test
    fun `anamorphic widescreen SD resolves to 16 by 9`() {
        assertEquals(16f / 9f, ratioOf(720, 576, par = 64f / 45f), 0.01f)
    }

    /**
     * Android throws IllegalArgumentException out of `enterPictureInPictureMode` for a ratio outside
     * roughly 1:2.39..2.39:1, so an absurd or corrupt reported size has to be clamped into range
     * rather than passed through - otherwise tapping the PiP button crashes the app.
     */
    @Test
    fun `an extreme ratio is clamped into the range Android accepts`() {
        val ultraWide = ratioOf(4000, 100)
        val ultraTall = ratioOf(100, 4000)

        assertTrue("$ultraWide must be <= 2.39", ultraWide <= 2.39f)
        assertTrue("$ultraTall must be >= 1/2.39", ultraTall >= 1f / 2.39f)
    }

    @Test
    fun `an unknown or audio-only size falls back to 16 by 9 instead of dividing by zero`() {
        assertEquals(Rational(16, 9), PipController.aspectRatioFor(0, 0))
        assertEquals(Rational(16, 9), PipController.aspectRatioFor(1920, 0))
        assertEquals(Rational(16, 9), PipController.aspectRatioFor(-1, -1))
    }

    /** media3 reports 0 or NaN for pixel aspect on some streams; that must degrade to square
     * pixels, not poison the ratio. */
    @Test
    fun `a missing or nonsensical pixel aspect is treated as square`() {
        assertEquals(16f / 9f, ratioOf(1920, 1080, par = 0f), 0.001f)
        assertEquals(16f / 9f, ratioOf(1920, 1080, par = Float.NaN), 0.001f)
        assertEquals(16f / 9f, ratioOf(1920, 1080, par = Float.POSITIVE_INFINITY), 0.001f)
    }
}
