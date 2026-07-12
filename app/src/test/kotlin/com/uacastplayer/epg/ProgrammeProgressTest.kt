package com.uacastplayer.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgrammeProgressTest {

    @Test
    fun `zero progress at the start`() {
        assertEquals(0f, ProgrammeProgress.progress(1000, 2000, 1000), 0.001f)
    }

    @Test
    fun `full progress at the end`() {
        assertEquals(1f, ProgrammeProgress.progress(1000, 2000, 2000), 0.001f)
    }

    @Test
    fun `half progress at the midpoint`() {
        assertEquals(0.5f, ProgrammeProgress.progress(1000, 2000, 1500), 0.001f)
    }

    @Test
    fun `clamps to zero before the start`() {
        assertEquals(0f, ProgrammeProgress.progress(1000, 2000, 500), 0.001f)
    }

    @Test
    fun `clamps to one after the end`() {
        assertEquals(1f, ProgrammeProgress.progress(1000, 2000, 3000), 0.001f)
    }

    @Test
    fun `zero-length window yields zero rather than dividing by zero`() {
        assertEquals(0f, ProgrammeProgress.progress(1000, 1000, 1000), 0.001f)
    }
}
