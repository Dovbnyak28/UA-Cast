package com.uacastplayer.diagnostics

import com.uacastplayer.diagnostics.CastRouteKind.DIRECT
import com.uacastplayer.diagnostics.CastRouteKind.PROXY_REMUX
import com.uacastplayer.diagnostics.CastRouteKind.PROXY_REWRITE
import com.uacastplayer.diagnostics.CastRouteOutcome.ATTEMPTED
import com.uacastplayer.diagnostics.CastRouteOutcome.FAILED
import com.uacastplayer.diagnostics.CastRouteOutcome.REACHED_PLAYING
import org.junit.Assert.assertEquals
import org.junit.Test

class RemuxEffectivenessPolicyTest {

    private fun apply(counts: RemuxEffectivenessCounts, route: CastRouteKind, outcome: CastRouteOutcome) =
        RemuxEffectivenessPolicy.apply(counts, route, outcome)

    @Test
    fun `direct attempted increments only directAttempted`() {
        val result = apply(RemuxEffectivenessCounts(), DIRECT, ATTEMPTED)

        assertEquals(RemuxEffectivenessCounts(directAttempted = 1), result)
    }

    @Test
    fun `direct reached playing increments only directPlaying`() {
        val result = apply(RemuxEffectivenessCounts(), DIRECT, REACHED_PLAYING)

        assertEquals(RemuxEffectivenessCounts(directPlaying = 1), result)
    }

    @Test
    fun `direct failed increments only directFailed`() {
        val result = apply(RemuxEffectivenessCounts(), DIRECT, FAILED)

        assertEquals(RemuxEffectivenessCounts(directFailed = 1), result)
    }

    @Test
    fun `remux events increment the remux bucket only`() {
        var counts = RemuxEffectivenessCounts()
        counts = apply(counts, PROXY_REMUX, ATTEMPTED)
        counts = apply(counts, PROXY_REMUX, REACHED_PLAYING)

        assertEquals(RemuxEffectivenessCounts(remuxAttempted = 1, remuxPlaying = 1), counts)
    }

    @Test
    fun `proxy rewrite events increment the proxy rewrite bucket only`() {
        var counts = RemuxEffectivenessCounts()
        counts = apply(counts, PROXY_REWRITE, ATTEMPTED)
        counts = apply(counts, PROXY_REWRITE, FAILED)

        assertEquals(RemuxEffectivenessCounts(proxyRewriteAttempted = 1, proxyRewriteFailed = 1), counts)
    }

    @Test
    fun `repeated events accumulate rather than overwrite`() {
        var counts = RemuxEffectivenessCounts()
        repeat(3) { counts = apply(counts, DIRECT, ATTEMPTED) }
        repeat(2) { counts = apply(counts, DIRECT, REACHED_PLAYING) }

        assertEquals(RemuxEffectivenessCounts(directAttempted = 3, directPlaying = 2), counts)
    }

    @Test
    fun `events on different routes never cross-contaminate buckets`() {
        var counts = RemuxEffectivenessCounts()
        counts = apply(counts, DIRECT, ATTEMPTED)
        counts = apply(counts, PROXY_REMUX, ATTEMPTED)
        counts = apply(counts, PROXY_REWRITE, ATTEMPTED)

        val expected = RemuxEffectivenessCounts(directAttempted = 1, remuxAttempted = 1, proxyRewriteAttempted = 1)
        assertEquals(expected, counts)
    }
}
