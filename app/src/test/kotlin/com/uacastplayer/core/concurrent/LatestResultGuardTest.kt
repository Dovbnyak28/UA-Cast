package com.uacastplayer.core.concurrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestResultGuardTest {
    @Test
    fun `only newest asynchronous operation may publish`() {
        val guard = LatestResultGuard()
        val slowOlderOperation = guard.next()
        val newerOperation = guard.next()

        assertFalse(guard.isCurrent(slowOlderOperation))
        assertTrue(guard.isCurrent(newerOperation))
    }

    @Test
    fun `invalidation rejects result without requiring replacement operation`() {
        val guard = LatestResultGuard()
        val operation = guard.next()

        guard.invalidate()

        assertFalse(guard.isCurrent(operation))
    }
}
