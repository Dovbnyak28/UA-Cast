package com.uacastplayer.cast

import com.uacastplayer.core.cast.AudioCodec
import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.TsSourceKind
import com.uacastplayer.core.cast.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class CastDeliveryStrategyTest {

    @Test
    fun `initial mode is Direct when not known incompatible`() {
        assertEquals(CastDeliveryMode.Direct, CastDeliveryStrategy.initialMode(isKnownIncompatible = false))
    }

    @Test
    fun `initial mode is Proxy when known incompatible`() {
        assertEquals(CastDeliveryMode.Proxy, CastDeliveryStrategy.initialMode(isKnownIncompatible = true))
    }

    @Test
    fun `an incompatible video verdict is blocked regardless of source kind`() {
        val verdict = CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Mpeg2Video)
        val expected = CastRouteDecision.Blocked(verdict)
        assertEquals(expected, CastDeliveryStrategy.onDiagnosticResult(verdict, TsSourceKind.RawTs))
        assertEquals(expected, CastDeliveryStrategy.onDiagnosticResult(verdict, TsSourceKind.Hls))
    }

    @Test
    fun `a LikelyCompatible verdict on raw TS skips straight to the proxy, same as Compatible`() {
        val verdict = CastCompatibilityVerdict.LikelyCompatible(audioHint = AudioCodec.MpegAudio, videoHint = null)
        val result = CastDeliveryStrategy.onDiagnosticResult(verdict, TsSourceKind.RawTs)
        assertEquals(CastRouteDecision.ProxyImmediately, result)
    }

    @Test
    fun `a LikelyCompatible verdict on HLS takes no action, same as Compatible`() {
        val verdict = CastCompatibilityVerdict.LikelyCompatible(audioHint = null, videoHint = VideoCodec.Hevc)
        val result = CastDeliveryStrategy.onDiagnosticResult(verdict, TsSourceKind.Hls)
        assertEquals(CastRouteDecision.NoAction, result)
    }

    @Test
    fun `a compatible raw-TS verdict skips straight to the proxy`() {
        val result = CastDeliveryStrategy.onDiagnosticResult(CastCompatibilityVerdict.Compatible, TsSourceKind.RawTs)
        assertEquals(CastRouteDecision.ProxyImmediately, result)
    }

    @Test
    fun `a compatible HLS verdict takes no action - direct-then-watchdog continues`() {
        val result = CastDeliveryStrategy.onDiagnosticResult(CastCompatibilityVerdict.Compatible, TsSourceKind.Hls)
        assertEquals(CastRouteDecision.NoAction, result)
    }

    @Test
    fun `an unknown verdict takes no action regardless of source kind`() {
        assertEquals(
            CastRouteDecision.NoAction,
            CastDeliveryStrategy.onDiagnosticResult(CastCompatibilityVerdict.Unknown, TsSourceKind.RawTs),
        )
        assertEquals(
            CastRouteDecision.NoAction,
            CastDeliveryStrategy.onDiagnosticResult(CastCompatibilityVerdict.Unknown, TsSourceKind.Hls),
        )
    }
}
