package com.uacastplayer.cast

import com.uacastplayer.core.cast.CastRouteKind
import com.uacastplayer.diagnostics.CastRouteOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CastRouteHistoryTest {
    private val observation = CastRouteObservation(
        "stream", "receiver", CastDeliveryMode.Proxy, CastRouteKind.PROXY_REWRITE,
    )
    private val remembered = mutableListOf<Pair<String, String>>()
    private val outcomes = mutableListOf<Pair<CastRouteKind, CastRouteOutcome>>()
    private val history = CastRouteHistory({ observation }, { url, id -> remembered += url to id }, { route, result ->
        outcomes += route to result
    })

    @Test
    fun `only successful proxy playback proves direct incompatibility and records it once`() {
        history.abandonDirect("stream")
        history.onStatus(ReceiverStatus.BUFFERING)
        assertEquals(emptyList<Pair<String, String>>(), remembered)
        repeat(3) { history.onStatus(ReceiverStatus.PLAYING) }
        assertEquals(listOf("stream" to "receiver"), remembered)
        assertEquals(1, outcomes.count { it.second == CastRouteOutcome.REACHED_PLAYING })
    }

    @Test
    fun `a delayed status for another stream does not teach incompatibility`() {
        history.abandonDirect("old-stream")
        history.onStatus(ReceiverStatus.PLAYING)
        assertEquals(emptyList<Pair<String, String>>(), remembered)
    }

    @Test
    fun `give up after a successful route does not count as a route that never worked`() {
        history.onStatus(ReceiverStatus.PLAYING)
        history.onGiveUp("stream", incompatible = false)
        assertEquals(emptyList<Pair<String, String>>(), remembered)
        assertEquals(0, outcomes.count { it.second == CastRouteOutcome.FAILED })
        history.reset()
        assertFalse(history.everReachedPlaying)
        history.onGiveUp("stream", incompatible = true)
        assertEquals(listOf("stream" to "receiver"), remembered)
        assertEquals(1, outcomes.count { it.second == CastRouteOutcome.FAILED })
    }
}
