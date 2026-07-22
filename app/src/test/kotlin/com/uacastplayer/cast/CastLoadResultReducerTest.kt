package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CastLoadResultReducerTest {

    @Test
    fun `success transitions to LOADED with no effects - PauseLocalPlayer already fired at load time`() {
        val result = CastLoadResultReducer.reduce(CastPlaybackState(), CastLoadResult.Success)
        assertEquals(CastLoadPhase.LOADED, result.state.loadPhase)
        assertEquals(emptyList<CastSideEffect>(), result.effects)
    }

    @Test
    fun `failure transitions to FAILED, records incompatibility, and resumes the local player`() {
        val result = CastLoadResultReducer.reduce(CastPlaybackState(), CastLoadResult.Failure("timeout"))
        assertEquals(CastLoadPhase.FAILED, result.state.loadPhase)
        assertEquals(
            listOf(CastSideEffect.RecordIncompatibility("timeout"), CastSideEffect.ResumeLocalPlayer),
            result.effects,
        )
    }

    @Test
    fun `failure preserves the reason text for later diagnostics`() {
        val result = CastLoadResultReducer.reduce(CastPlaybackState(), CastLoadResult.Failure("unsupported_codec"))
        val recordEffect = result.effects.filterIsInstance<CastSideEffect.RecordIncompatibility>().single()
        assertEquals("unsupported_codec", recordEffect.reason)
    }

    @Test
    fun `success does not alter unrelated state fields`() {
        val initial = CastPlaybackState(isSessionConnected = true, pendingChannelIndex = 3)
        val result = CastLoadResultReducer.reduce(initial, CastLoadResult.Success)
        assertEquals(true, result.state.isSessionConnected)
        assertEquals(3, result.state.pendingChannelIndex)
    }

    @Test
    fun `repeated success calls remain idempotent in effect shape`() {
        val first = CastLoadResultReducer.reduce(CastPlaybackState(), CastLoadResult.Success)
        val second = CastLoadResultReducer.reduce(first.state, CastLoadResult.Success)
        assertTrue(second.state.loadPhase == CastLoadPhase.LOADED)
        assertEquals(first.effects, second.effects)
    }
}
