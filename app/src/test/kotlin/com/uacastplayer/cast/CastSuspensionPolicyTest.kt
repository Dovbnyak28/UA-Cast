package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastSuspensionPolicyTest {
    @Test fun `suspension retains ownership and route without falsely showing playing`() {
        val state = CastSuspensionPolicy.suspend(CastPlaybackState(
            isSessionConnected = true, receiverStatus = ReceiverStatus.PLAYING,
            deliveryMode = CastDeliveryMode.Proxy, pendingChannelIndex = 3,
        ))
        assertTrue(state.isSessionConnected)
        assertTrue(state.isSessionSuspended)
        assertTrue(state.isRecovering)
        assertEquals(CastDeliveryMode.Proxy, state.deliveryMode)
        assertEquals(3, state.pendingChannelIndex)
        assertEquals(ReceiverStatus.BUFFERING, state.receiverStatus)
        val ended = CastReceiverStatusReducer.reduce(state, ReceiverStatus.DISCONNECTED)
        assertFalse(ended.state.isSessionSuspended)
        assertTrue(CastSideEffect.CloseProxySession in ended.effects)
        assertTrue(CastSideEffect.ResumeLocalPlayer in ended.effects)
    }

    @Test fun `healthy unchanged resumed media is retained including user pause`() {
        listOf(ReceiverStatus.PLAYING, ReceiverStatus.PAUSED).forEach {
            assertTrue(CastSuspensionPolicy.canKeepMedia(true, "a", "a", it))
        }
    }

    @Test fun `switched channel stale media idle and absent content must reload`() {
        assertFalse(CastSuspensionPolicy.canKeepMedia(false, "a", "a", ReceiverStatus.PLAYING))
        assertFalse(CastSuspensionPolicy.canKeepMedia(true, "a", "b", ReceiverStatus.PLAYING))
        assertFalse(CastSuspensionPolicy.canKeepMedia(true, "a", "a", ReceiverStatus.IDLE))
        assertFalse(CastSuspensionPolicy.canKeepMedia(true, null, null, ReceiverStatus.PLAYING))
    }
}
