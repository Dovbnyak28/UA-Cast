package com.uacastplayer.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionAccessibilityTest {

    @Test
    fun `zero animator scale disables decorative motion`() {
        assertFalse(animationsAllowedFor(0f))
        assertTrue(animationsAllowedFor(0.5f))
        assertTrue(animationsAllowedFor(1f))
    }
}
