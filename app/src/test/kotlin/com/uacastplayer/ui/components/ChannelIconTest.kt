package com.uacastplayer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelIconTest {

    @Test
    fun `a single word takes its first letter`() {
        assertEquals("N", initialsFor("News"))
    }

    @Test
    fun `two words take one letter each`() {
        assertEquals("BS", initialsFor("Best Sports"))
    }

    @Test
    fun `more than two words still takes only the first two`() {
        assertEquals("AB", initialsFor("A B C D"))
    }

    @Test
    fun `repeated whitespace between words is collapsed`() {
        assertEquals("AB", initialsFor("A   B"))
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        assertEquals("N", initialsFor("  News  "))
    }

    @Test
    fun `letters are uppercased`() {
        assertEquals("AB", initialsFor("alpha beta"))
    }

    @Test
    fun `an empty name yields no initials`() {
        assertEquals("", initialsFor(""))
    }
}
