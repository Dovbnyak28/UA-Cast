package com.uacastplayer.cast

import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.DiagnosticCachePolicy
import com.uacastplayer.core.cast.TsProgramInfo
import com.uacastplayer.core.cast.TsSourceKind
import com.uacastplayer.core.cast.VideoCodec
import com.uacastplayer.data.cast.TsDiagnosticResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CastDiagnosticCoordinatorTest {

    @Test
    fun `warm-up waits for the debounce and caches its result`() = runTest {
        var activeUrl: String? = "one"
        var calls = 0
        val coordinator = CastDiagnosticCoordinator(
            scope = backgroundScope,
            activeStreamUrl = { activeUrl },
            diagnose = {
                calls++
                TsDiagnosticResult(TsProgramInfo(VideoCodec.H264, emptyList()), TsSourceKind.RawTs)
            },
            nowMillis = { 100L },
        )

        coordinator.scheduleWarmup("one")
        runCurrent()
        assertEquals(0, calls)

        advanceTimeBy(DiagnosticCachePolicy.DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(1, calls)
        assertEquals(TsSourceKind.RawTs, coordinator.cached("one")?.sourceKind)
    }

    @Test
    fun `a replacement warm-up cancels the previous channel`() = runTest {
        var activeUrl: String? = "one"
        val diagnosed = mutableListOf<String>()
        val coordinator = CastDiagnosticCoordinator(
            scope = backgroundScope,
            activeStreamUrl = { activeUrl },
            diagnose = { url ->
                diagnosed += url
                TsDiagnosticResult(null, TsSourceKind.Unknown)
            },
        )

        coordinator.scheduleWarmup("one")
        activeUrl = "two"
        coordinator.scheduleWarmup("two")
        advanceTimeBy(DiagnosticCachePolicy.DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(listOf("two"), diagnosed)
    }

    @Test
    fun `cancel prevents a pending warm-up from opening the network probe`() = runTest {
        var calls = 0
        val coordinator = CastDiagnosticCoordinator(
            scope = backgroundScope,
            activeStreamUrl = { "one" },
            diagnose = {
                calls++
                TsDiagnosticResult(null, TsSourceKind.Unknown)
            },
        )

        coordinator.scheduleWarmup("one")
        coordinator.cancelWarmup()
        advanceTimeBy(DiagnosticCachePolicy.DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(0, calls)
    }

    @Test
    fun `a result for a channel switched away from is neither returned nor cached`() = runTest {
        var activeUrl: String? = "one"
        val coordinator = CastDiagnosticCoordinator(
            scope = backgroundScope,
            activeStreamUrl = { activeUrl },
            diagnose = {
                activeUrl = "two"
                TsDiagnosticResult(TsProgramInfo(VideoCodec.H264, emptyList()), TsSourceKind.RawTs)
            },
        )

        assertNull(coordinator.probe("one"))
        assertNull(coordinator.cached("one"))
    }

    @Test
    fun `probe returns and stores the compatibility verdict actually merged`() = runTest {
        val coordinator = CastDiagnosticCoordinator(
            scope = backgroundScope,
            activeStreamUrl = { "one" },
            diagnose = {
                TsDiagnosticResult(TsProgramInfo(VideoCodec.Mpeg2Video, emptyList()), TsSourceKind.RawTs)
            },
            nowMillis = { 200L },
        )

        val result = coordinator.probe("one")

        assertTrue(result?.first is CastCompatibilityVerdict.IncompatibleVideo)
        assertEquals(TsSourceKind.RawTs, result?.second)
        assertEquals(result?.first, coordinator.cached("one")?.verdict)
    }
}
