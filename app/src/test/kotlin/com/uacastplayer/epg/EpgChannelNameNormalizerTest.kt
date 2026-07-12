package com.uacastplayer.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgChannelNameNormalizerTest {

    @Test
    fun `strips a trailing HD suffix`() {
        assertEquals("bbc one", EpgChannelNameNormalizer.normalize("BBC One HD"))
    }

    @Test
    fun `strips a trailing 4K suffix`() {
        assertEquals("nat geo", EpgChannelNameNormalizer.normalize("Nat Geo 4K"))
    }

    @Test
    fun `strips a bracketed quality suffix`() {
        assertEquals("discovery", EpgChannelNameNormalizer.normalize("Discovery (HD)"))
    }

    @Test
    fun `is case insensitive and trims whitespace`() {
        assertEquals("news channel", EpgChannelNameNormalizer.normalize("  News Channel  "))
    }

    @Test
    fun `treats yo and ye as equivalent`() {
        assertEquals(
            EpgChannelNameNormalizer.normalize("Метеор"),
            EpgChannelNameNormalizer.normalize("Метёор"),
        )
    }

    @Test
    fun `does not alter a name with no quality suffix`() {
        assertEquals("cartoon network", EpgChannelNameNormalizer.normalize("Cartoon Network"))
    }
}
