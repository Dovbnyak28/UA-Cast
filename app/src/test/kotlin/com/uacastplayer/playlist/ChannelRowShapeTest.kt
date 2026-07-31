package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelRowShapeTest {

    @Test
    fun `single item list rounds both top and bottom`() {
        val rounding = ChannelRowShape.roundingFor(index = 0, lastIndex = 0)

        assertEquals(ChannelRowShape.Rounding(top = true, bottom = true), rounding)
    }

    @Test
    fun `first of two items rounds only the top`() {
        val rounding = ChannelRowShape.roundingFor(index = 0, lastIndex = 1)

        assertEquals(ChannelRowShape.Rounding(top = true, bottom = false), rounding)
    }

    @Test
    fun `last of two items rounds only the bottom`() {
        val rounding = ChannelRowShape.roundingFor(index = 1, lastIndex = 1)

        assertEquals(ChannelRowShape.Rounding(top = false, bottom = true), rounding)
    }

    @Test
    fun `middle item of three or more rounds neither corner`() {
        val rounding = ChannelRowShape.roundingFor(index = 1, lastIndex = 2)

        assertEquals(ChannelRowShape.Rounding(top = false, bottom = false), rounding)
    }

    @Test
    fun `first item of three or more rounds only the top`() {
        val rounding = ChannelRowShape.roundingFor(index = 0, lastIndex = 2)

        assertEquals(ChannelRowShape.Rounding(top = true, bottom = false), rounding)
    }

    @Test
    fun `last item of three or more rounds only the bottom`() {
        val rounding = ChannelRowShape.roundingFor(index = 2, lastIndex = 2)

        assertEquals(ChannelRowShape.Rounding(top = false, bottom = true), rounding)
    }
}
