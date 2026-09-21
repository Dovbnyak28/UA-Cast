package com.uacastplayer.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaConnectionAttemptPolicyTest {
    private val first = DlnaDevice("First", "http://first/control")
    private val second = DlnaDevice("Second", "http://second/control")
    private val third = DlnaDevice("Third", "http://third/control")

    @Test
    fun `handoff stops both a still-connected and an in-flight prior target`() {
        assertEquals(
            listOf(second, first),
            DlnaConnectionAttemptPolicy.previousDevices(second, first, third),
        )
    }

    @Test
    fun `handoff does not stop the target renderer itself`() {
        assertEquals(
            listOf(first),
            DlnaConnectionAttemptPolicy.previousDevices(second, first, second),
        )
    }

    @Test
    fun `a superseded attempt cannot commit state`() {
        assertFalse(DlnaConnectionAttemptPolicy.shouldCommit(4L, 5L))
        assertTrue(DlnaConnectionAttemptPolicy.shouldCommit(5L, 5L))
    }
}
