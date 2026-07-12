package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameQualityBadgeTest {

    @Test
    fun `detects HD marker as a whole word`() {
        assertEquals("HD", NameQualityBadge.detect("BBC One HD"))
    }

    @Test
    fun `detects 4K marker`() {
        assertEquals("4K", NameQualityBadge.detect("Nat Geo 4K"))
    }

    @Test
    fun `prefers the highest quality marker when multiple are present`() {
        assertEquals("4K", NameQualityBadge.detect("Sports 4K UHD"))
    }

    @Test
    fun `does not match HD as a substring of another word`() {
        assertNull(NameQualityBadge.detect("CHDTV Network"))
    }

    @Test
    fun `returns null when no marker is present`() {
        assertNull(NameQualityBadge.detect("Discovery Channel"))
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals("HD", NameQualityBadge.detect("channel hd"))
    }

    @Test
    fun `detects SD marker`() {
        assertEquals("SD", NameQualityBadge.detect("News SD"))
    }
}
