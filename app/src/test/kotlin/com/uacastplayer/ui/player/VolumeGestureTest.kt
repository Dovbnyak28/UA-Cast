package com.uacastplayer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The volume drag on the fullscreen player, including the case where the system says no.
 *
 * The refusal is a real, documented one rather than a hypothetical: `AudioManager.setStreamVolume`
 * declares a `SecurityException` when the change would touch Do Not Disturb and the caller has no
 * notification policy access, which this app deliberately does not ask for. It cannot be produced
 * on a test runner, so the call it comes out of is handed in as a function - which is the only
 * reason the guard around it can be asserted at all.
 */
class VolumeGestureTest {

    private companion object {
        const val MAX_VOLUME = 15
    }

    @Test
    fun `a drag is mapped onto the stream's own volume steps`() {
        val asked = mutableListOf<Int>()

        applyStreamVolume(max = MAX_VOLUME, level = 0f) { asked += it }
        applyStreamVolume(max = MAX_VOLUME, level = 0.5f) { asked += it }
        applyStreamVolume(max = MAX_VOLUME, level = 1f) { asked += it }

        assertEquals(listOf(0, 7, MAX_VOLUME), asked)
    }

    /** A drag can run past either end of the bar before the gesture is released. */
    @Test
    fun `a level outside the bar is clamped to it rather than passed on`() {
        val asked = mutableListOf<Int>()

        applyStreamVolume(max = MAX_VOLUME, level = -0.4f) { asked += it }
        applyStreamVolume(max = MAX_VOLUME, level = 1.8f) { asked += it }

        assertEquals(listOf(0, MAX_VOLUME), asked)
    }

    /**
     * Do Not Disturb refusing the change must not leave the pointer handler.
     *
     * With Do Not Disturb in total-silence mode the media stream is silenced too, so this is what an
     * ordinary volume drag does on a phone whose owner has switched it on - a gesture that is
     * unlabelled and easy to find by accident, on the screen the app exists for.
     */
    @Test
    fun `a refusal from Do Not Disturb is not allowed to reach the gesture`() {
        applyStreamVolume(max = MAX_VOLUME, level = 0.5f) {
            throw SecurityException("Not allowed to change Do Not disturb state")
        }
    }
}
