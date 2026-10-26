package com.uacastplayer.player

import androidx.media3.ui.AspectRatioFrameLayout
import com.uacastplayer.core.settings.PlayerResizeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ResizeModeCycleTest {

    @Test
    fun `FIT advances to FILL`() {
        assertEquals(PlayerResizeMode.FILL, ResizeModeCycle.next(PlayerResizeMode.FIT))
    }

    @Test
    fun `FILL advances to ZOOM`() {
        assertEquals(PlayerResizeMode.ZOOM, ResizeModeCycle.next(PlayerResizeMode.FILL))
    }

    @Test
    fun `ZOOM wraps back to FIT`() {
        assertEquals(PlayerResizeMode.FIT, ResizeModeCycle.next(PlayerResizeMode.ZOOM))
    }

    @Test
    fun `cycling three times returns to the start`() {
        var mode = PlayerResizeMode.FIT
        repeat(3) { mode = ResizeModeCycle.next(mode) }
        assertEquals(PlayerResizeMode.FIT, mode)
    }

    @Test
    fun `each mode maps to a distinct Media3 resize mode`() {
        val mapped = PlayerResizeMode.entries.map(ResizeModeCycle::toMedia3ResizeMode)
        assertEquals(mapped.distinct(), mapped)
    }

    @Test
    fun `FIT maps to the Media3 FIT constant`() {
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, ResizeModeCycle.toMedia3ResizeMode(PlayerResizeMode.FIT))
    }

    @Test
    fun `each mode maps to a distinct label resource`() {
        val labels = PlayerResizeMode.entries.map(ResizeModeCycle::labelRes)
        assertEquals(labels.distinct(), labels)
    }

    @Test
    fun `next never returns the same mode`() {
        for (mode in PlayerResizeMode.entries) {
            assertNotEquals(mode, ResizeModeCycle.next(mode))
        }
    }
}
