package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

private const val TOLERANCE = 0.001f

class DepthTest {

    @Test
    fun `lighten at zero fraction returns the original color`() {
        val color = Color(0xFF334455)
        assertColorEquals(color, lighten(color, 0f))
    }

    @Test
    fun `lighten at full fraction reaches white, preserving alpha`() {
        val color = Color(0x80334455)
        val result = lighten(color, 1f)
        assertColorEquals(Color.White.copy(alpha = color.alpha), result)
    }

    @Test
    fun `lighten clamps fractions outside 0f to 1f`() {
        val color = Color(0xFF334455)
        assertColorEquals(lighten(color, 1f), lighten(color, 5f))
        assertColorEquals(lighten(color, 0f), lighten(color, -5f))
    }

    @Test
    fun `darken at zero fraction returns the original color`() {
        val color = Color(0xFF334455)
        assertColorEquals(color, darken(color, 0f))
    }

    @Test
    fun `darken at full fraction reaches black, preserving alpha`() {
        val color = Color(0x80334455)
        val result = darken(color, 1f)
        assertColorEquals(Color.Black.copy(alpha = color.alpha), result)
    }

    @Test
    fun `darken clamps fractions outside 0f to 1f`() {
        val color = Color(0xFF334455)
        assertColorEquals(darken(color, 1f), darken(color, 5f))
        assertColorEquals(darken(color, 0f), darken(color, -5f))
    }

    private fun assertColorEquals(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, TOLERANCE)
        assertEquals(expected.green, actual.green, TOLERANCE)
        assertEquals(expected.blue, actual.blue, TOLERANCE)
        assertEquals(expected.alpha, actual.alpha, TOLERANCE)
    }
}
