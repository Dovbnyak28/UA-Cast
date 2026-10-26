package com.uacastplayer.ui.components

import com.uacastplayer.playlist.M3uChannel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ArtworkToneResolutionTest {

    private val channel = M3uChannel("Channel", "https://example.test/live")

    @Test
    fun `resolver cancellation is propagated to the compose producer`() {
        val expected = CancellationException("channel changed")

        val actual = assertThrows(CancellationException::class.java) {
            runTest { resolveArtworkToneFile(channel) { throw expected } }
        }

        assertSame(expected, actual)
    }

    @Test
    fun `ordinary resolver IO failure becomes no artwork tone`() = runTest {
        val resolved = resolveArtworkToneFile(channel) { throw IOException("cache disappeared") }

        assertNull(resolved)
    }
}
