package com.uacastplayer.core.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticCachePolicyTest {

    @Test
    fun `a Compatible entry is valid indefinitely - well past the Unknown TTL`() {
        val entry = CachedDiagnostic(CastCompatibilityVerdict.Compatible, TsSourceKind.Hls, cachedAtMillis = 0L)
        assertTrue(DiagnosticCachePolicy.isValid(entry, nowMillis = DiagnosticCachePolicy.UNKNOWN_TTL_MILLIS * 100))
    }

    @Test
    fun `an IncompatibleVideo entry is valid indefinitely`() {
        val verdict = CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Mpeg2Video)
        val entry = CachedDiagnostic(verdict, TsSourceKind.RawTs, cachedAtMillis = 0L)
        assertTrue(DiagnosticCachePolicy.isValid(entry, nowMillis = DiagnosticCachePolicy.UNKNOWN_TTL_MILLIS * 100))
    }

    @Test
    fun `an Unknown entry is valid within its TTL`() {
        val entry = CachedDiagnostic(CastCompatibilityVerdict.Unknown, TsSourceKind.Unknown, cachedAtMillis = 0L)
        assertTrue(DiagnosticCachePolicy.isValid(entry, nowMillis = DiagnosticCachePolicy.UNKNOWN_TTL_MILLIS - 1))
    }

    @Test
    fun `an Unknown entry expires once its TTL elapses`() {
        val entry = CachedDiagnostic(CastCompatibilityVerdict.Unknown, TsSourceKind.Unknown, cachedAtMillis = 0L)
        assertFalse(DiagnosticCachePolicy.isValid(entry, nowMillis = DiagnosticCachePolicy.UNKNOWN_TTL_MILLIS))
    }

    @Test
    fun `no previous verdict always accepts the candidate`() {
        val result = DiagnosticCachePolicy.strongerOf(previous = null, candidate = CastCompatibilityVerdict.Compatible)
        assertEquals(CastCompatibilityVerdict.Compatible, result)
    }

    @Test
    fun `a more decisive candidate replaces a weaker previous verdict`() {
        val candidate = CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Mpeg2Video)
        val previous = CastCompatibilityVerdict.Unknown
        val result = DiagnosticCachePolicy.strongerOf(previous, candidate)
        assertEquals(candidate, result)
    }

    @Test
    fun `IncompatibleVideo is never replaced by a less decisive later probe`() {
        val previous = CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Mpeg2Video)
        val result = DiagnosticCachePolicy.strongerOf(previous, candidate = CastCompatibilityVerdict.Compatible)
        assertEquals(previous, result)
    }

    @Test
    fun `IncompatibleVideo is never replaced by a later Unknown probe`() {
        val previous = CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Mpeg2Video)
        val result = DiagnosticCachePolicy.strongerOf(previous, candidate = CastCompatibilityVerdict.Unknown)
        assertEquals(previous, result)
    }

    @Test
    fun `equal-rank verdicts let the later candidate win`() {
        val previous = CastCompatibilityVerdict.Compatible
        val candidate = CastCompatibilityVerdict.LikelyCompatible(audioHint = null, videoHint = VideoCodec.Hevc)
        val result = DiagnosticCachePolicy.strongerOf(previous, candidate)
        assertEquals(candidate, result)
    }
}
