package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastProxyFailureReducerTest {

    @Test
    fun `local proxy preparation failure reports failure and resumes local playback`() {
        val initial = CastPlaybackState(
            isSessionConnected = true,
            loadPhase = CastLoadPhase.LOADING,
            deliveryMode = CastDeliveryMode.Proxy,
            isRecovering = true,
            recoveringWithoutPlayback = true,
        )

        val result = CastProxyFailureReducer.reduce(initial)

        assertEquals(CastLoadPhase.FAILED, result.state.loadPhase)
        assertEquals(CastDeliveryMode.Direct, result.state.deliveryMode)
        assertTrue(result.state.receiverLoadFailed)
        assertFalse(result.state.isRecovering)
        assertFalse(result.state.recoveringWithoutPlayback)
        assertEquals(listOf(CastSideEffect.ResumeLocalPlayer), result.effects)
    }

    @Test
    fun `phone infrastructure failure is not recorded as stream incompatibility`() {
        val result = CastProxyFailureReducer.reduce(CastPlaybackState())

        assertTrue(result.effects.none { it is CastSideEffect.RecordIncompatibility })
    }
}

