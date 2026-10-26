package com.uacastplayer.cast

import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.TsSourceKind
import com.uacastplayer.core.cast.VideoCodec
import com.uacastplayer.data.cast.ProxySourceDiagnostic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastDiagnosticCoordinatorTest {
    private val channel = CastChannel(0, "https://origin.example/live", "One")
    private val compatible = ProxySourceDiagnostic(CastCompatibilityVerdict.Compatible, TsSourceKind.RawTs)

    @Test fun `an observation is required before any cache entry exists`() {
        val coordinator = CastDiagnosticCoordinator(nowMillis = { 100L })
        assertNull(coordinator.cached(channel))
        coordinator.record(channel, compatible)
        assertEquals(TsSourceKind.RawTs, coordinator.cached(channel)?.sourceKind)
        assertEquals(100L, coordinator.cached(channel)?.cachedAtMillis)
    }

    @Test fun `reading metadata repeatedly never changes or refreshes it`() {
        var now = 100L
        val coordinator = CastDiagnosticCoordinator(nowMillis = { now })
        coordinator.record(channel, compatible)
        now = 200L
        repeat(20) { assertEquals(100L, coordinator.cached(channel)?.cachedAtMillis) }
    }

    @Test fun `URL and access headers isolate compatibility verdicts`() {
        val coordinator = CastDiagnosticCoordinator()
        coordinator.record(channel, compatible)
        assertNull(coordinator.cached(channel.copy(streamUrl = "https://origin.example/two")))
        assertNull(coordinator.cached(channel.copy(userAgent = "AnotherPlayer")))
        assertNull(coordinator.cached(channel.copy(referrer = "https://provider.example/player")))
    }

    @Test fun `sanitization matches the proxy resource and display fields do not affect identity`() {
        val coordinator = CastDiagnosticCoordinator()
        coordinator.record(channel, compatible)
        val renamed = channel.copy(index = 20, title = "Renamed", userAgent = "bad\r\nheader", referrer = "bad\nvalue")
        assertEquals(compatible.verdict, coordinator.cached(renamed)?.verdict)
    }

    @Test fun `record returns and stores the strongest compatibility verdict`() {
        val coordinator = CastDiagnosticCoordinator()
        val blocked = CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Mpeg2Video)
        coordinator.record(channel, ProxySourceDiagnostic(blocked, TsSourceKind.RawTs))
        val result = coordinator.record(channel, compatible)
        assertTrue(result is CastCompatibilityVerdict.IncompatibleVideo)
        assertEquals(blocked, coordinator.cached(channel)?.verdict)
    }
}
