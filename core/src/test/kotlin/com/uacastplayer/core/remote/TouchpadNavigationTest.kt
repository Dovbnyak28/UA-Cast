package com.uacastplayer.core.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TouchpadNavigationTest {
    @Test fun `small motion accumulates until the threshold`() {
        val pad = TouchpadNavigation(10f)
        assertNull(pad.drag(4f, 0f))
        assertNull(pad.drag(5f, 0f))
        assertEquals(RemoteCommand.RIGHT, pad.drag(1f, 0f))
        assertNull(pad.drag(1f, 0f))
    }
    @Test fun `dominant direction maps to D-pad commands`() {
        listOf(12f to 1f, -12f to 1f, 1f to 12f, 1f to -12f).zip(
            listOf(RemoteCommand.RIGHT, RemoteCommand.LEFT, RemoteCommand.DOWN, RemoteCommand.UP),
        ).forEach { (delta, expected) ->
            assertEquals(expected, TouchpadNavigation(10f).drag(delta.first, delta.second))
        }
    }
    @Test fun `reset retires an incomplete gesture`() {
        val pad = TouchpadNavigation(10f)
        assertNull(pad.drag(9f, 0f))
        pad.reset()
        assertNull(pad.drag(2f, 0f))
    }
    @Test fun `nonfinite input cannot poison the next gesture`() {
        val pad = TouchpadNavigation(10f)
        assertNull(pad.drag(Float.NaN, 20f))
        assertNull(pad.drag(Float.POSITIVE_INFINITY, 20f))
        assertEquals(RemoteCommand.UP, pad.drag(0f, -10f))
    }
    @Test fun `one large motion cannot flood the command lane`() {
        val pad = TouchpadNavigation(10f)
        assertEquals(RemoteCommand.RIGHT, pad.drag(10_000f, 0f))
        assertNull(pad.drag(0f, 0f))
    }
    @Test fun `invalid sensitivity is rejected`() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertThrows(IllegalArgumentException::class.java) { TouchpadNavigation(it) }
        }
    }
}
