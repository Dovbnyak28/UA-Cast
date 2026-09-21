package com.uacastplayer.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastLoadGenerationTest {
    @Test
    fun `new load supersedes previous callback`() {
        val generations = CastLoadGeneration()
        val first = generations.next()
        val second = generations.next()

        assertFalse(generations.isCurrent(first))
        assertTrue(generations.isCurrent(second))
    }

    @Test
    fun `session transition invalidates callback without replacement load`() {
        val generations = CastLoadGeneration()
        val endedSessionLoad = generations.next()

        generations.invalidate()

        assertFalse(generations.isCurrent(endedSessionLoad))
    }
}
