package com.uacastplayer.data.cast

import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.TsSourceKind
import com.uacastplayer.core.cast.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiagnosticResultCacheTest {

    private val cache = DiagnosticResultCache()

    @Test
    fun `an unmerged key returns no entry`() {
        assertNull(cache.get("https://origin/a.m3u8", nowMillis = 0L))
    }

    @Test
    fun `a merged entry is retrievable by the same key`() {
        cache.merge("https://origin/a.m3u8", CastCompatibilityVerdict.Compatible, TsSourceKind.Hls, nowMillis = 0L)
        val entry = cache.get("https://origin/a.m3u8", nowMillis = 1_000L)
        assertEquals(CastCompatibilityVerdict.Compatible, entry?.verdict)
        assertEquals(TsSourceKind.Hls, entry?.sourceKind)
    }

    @Test
    fun `an expired Unknown entry is not returned`() {
        cache.merge("https://origin/a.m3u8", CastCompatibilityVerdict.Unknown, TsSourceKind.Unknown, nowMillis = 0L)
        val stillFresh = cache.get("https://origin/a.m3u8", nowMillis = 1_000L)
        val expired = cache.get("https://origin/a.m3u8", nowMillis = 20 * 60_000L)
        assertEquals(CastCompatibilityVerdict.Unknown, stillFresh?.verdict)
        assertNull(expired)
    }

    @Test
    fun `merge never lets a later probe downgrade a confirmed IncompatibleVideo`() {
        val key = "https://origin/a.m3u8"
        val incompatible = CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Mpeg2Video)
        cache.merge(key, incompatible, TsSourceKind.RawTs, nowMillis = 0L)
        cache.merge(key, CastCompatibilityVerdict.Compatible, TsSourceKind.RawTs, nowMillis = 1_000L)
        val entry = cache.get(key, nowMillis = 2_000L)
        assertEquals(CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Mpeg2Video), entry?.verdict)
    }

    @Test
    fun `distinct keys are cached independently`() {
        cache.merge("https://origin/a.m3u8", CastCompatibilityVerdict.Compatible, TsSourceKind.Hls, nowMillis = 0L)
        assertNull(cache.get("https://origin/b.m3u8", nowMillis = 0L))
    }
}
