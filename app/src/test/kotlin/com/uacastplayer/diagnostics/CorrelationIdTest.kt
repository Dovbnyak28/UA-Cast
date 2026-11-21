package com.uacastplayer.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CorrelationIdTest {

    @Test
    fun `identifier is stable short and does not expose raw token`() {
        val raw = "receiver-session-secret"
        val first = CorrelationId.from("cast", raw)

        assertEquals(first, CorrelationId.from("cast", raw))
        assertEquals("cast-".length + 10, first.length)
        assertFalse(first.contains(raw))
        assertNotEquals(first, CorrelationId.from("cast", "$raw-other"))
    }
}
