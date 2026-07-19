package com.uacastplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class BrightnessGestureStartTest {

    @Test
    fun `uses the window override when one is already set`() {
        assertEquals(0.8f, BrightnessGestureStart.level(windowOverride = 0.8f, systemBrightness = 0.3f), 0f)
    }

    @Test
    fun `falls back to system brightness when the window is in auto mode`() {
        assertEquals(0.3f, BrightnessGestureStart.level(windowOverride = -1f, systemBrightness = 0.3f), 0f)
    }

    @Test
    fun `falls back to a fixed default when auto mode and system brightness are both unreadable`() {
        assertEquals(0.5f, BrightnessGestureStart.level(windowOverride = -1f, systemBrightness = null), 0f)
    }
}
