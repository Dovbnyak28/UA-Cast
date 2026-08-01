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

    @Test
    fun `a blank name yields no initials`() {
        assertEquals("", initialsFor("   "))
    }

    @Test
    fun `tabs and newlines separate words like spaces do`() {
        assertEquals("AB", initialsFor("alpha\t\nbeta"))
    }

    /** Only whitespace splits words - punctuation inside one does not, so "1+1" contributes its
     * leading digit and nothing more. */
    @Test
    fun `a non-letter first character is taken as-is`() {
        assertEquals("1U", initialsFor("1+1 Ukraine"))
    }

    @Test
    fun `cyrillic initials are uppercased`() {
        assertEquals("НК", initialsFor("новий канал"))
    }

    /** The scan stops at two rather than splitting a long name in full and discarding the rest -
     * this pins that the early exit doesn't change the answer. */
    @Test
    fun `a long name yields the same two initials as a short one`() {
        assertEquals("AB", initialsFor("Alpha Beta Gamma Delta Epsilon Zeta Eta Theta"))
    }
}
