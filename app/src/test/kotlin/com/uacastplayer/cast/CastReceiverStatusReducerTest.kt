package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastReceiverStatusReducerTest {

    @Test
    fun `BUFFERING updates status with no side effects`() {
        val result = CastReceiverStatusReducer.reduce(CastPlaybackState(), ReceiverStatus.BUFFERING)
        assertEquals(ReceiverStatus.BUFFERING, result.state.receiverStatus)
        assertTrue(result.state.isSessionConnected)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `PLAYING pauses the local player`() {
        val result = CastReceiverStatusReducer.reduce(CastPlaybackState(), ReceiverStatus.PLAYING)
        assertEquals(ReceiverStatus.PLAYING, result.state.receiverStatus)
        assertEquals(listOf(CastSideEffect.PauseLocalPlayer), result.effects)
    }

    @Test
    fun `PAUSED updates status with no side effects`() {
        val result = CastReceiverStatusReducer.reduce(CastPlaybackState(), ReceiverStatus.PAUSED)
        assertEquals(ReceiverStatus.PAUSED, result.state.receiverStatus)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `IDLE with ERROR records incompatibility, closes the proxy, and resumes local playback`() {
        val result = CastReceiverStatusReducer.reduce(CastPlaybackState(), ReceiverStatus.IDLE, IdleReason.ERROR)
        assertEquals(ReceiverStatus.IDLE, result.state.receiverStatus)
        assertEquals(IdleReason.ERROR, result.state.idleReason)
        assertEquals(
            listOf(
                CastSideEffect.RecordIncompatibility("receiver_idle_error"),
                CastSideEffect.CloseProxySession,
                CastSideEffect.ResumeLocalPlayer,
            ),
            result.effects,
        )
    }

    @Test
    fun `IDLE with FINISHED only closes the proxy session`() {
        val result = CastReceiverStatusReducer.reduce(CastPlaybackState(), ReceiverStatus.IDLE, IdleReason.FINISHED)
        assertEquals(listOf(CastSideEffect.CloseProxySession), result.effects)
    }

    @Test
    fun `IDLE with CANCELLED has no special side effects`() {
        val result = CastReceiverStatusReducer.reduce(CastPlaybackState(), ReceiverStatus.IDLE, IdleReason.CANCELLED)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `IDLE with INTERRUPTED has no special side effects`() {
        val result = CastReceiverStatusReducer.reduce(CastPlaybackState(), ReceiverStatus.IDLE, IdleReason.INTERRUPTED)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `DISCONNECTED resumes local playback and closes the proxy session`() {
        val connected = CastPlaybackState(isSessionConnected = true, receiverStatus = ReceiverStatus.PLAYING)
        val result = CastReceiverStatusReducer.reduce(connected, ReceiverStatus.DISCONNECTED)
        assertFalse(result.state.isSessionConnected)
        assertEquals(ReceiverStatus.DISCONNECTED, result.state.receiverStatus)
        assertEquals(CastLoadPhase.IDLE, result.state.loadPhase)
        assertEquals(
            listOf(CastSideEffect.ResumeLocalPlayer, CastSideEffect.CloseProxySession),
            result.effects,
        )
    }

    @Test
    fun `DISCONNECTED with a pending channel switch applies it and clears the pending slot`() {
        val stateWithPending = CastPlaybackState(isSessionConnected = true, pendingChannelIndex = 7)
        val result = CastReceiverStatusReducer.reduce(stateWithPending, ReceiverStatus.DISCONNECTED)
        assertEquals(
            listOf(
                CastSideEffect.ResumeLocalPlayer,
                CastSideEffect.CloseProxySession,
                CastSideEffect.ApplyPendingChannelSwitch(7),
            ),
            result.effects,
        )
        assertNull(result.state.pendingChannelIndex)
    }

    @Test
    fun `requestChannelSwitch queues the index without emitting any side effects`() {
        val newState = CastReceiverStatusReducer.requestChannelSwitch(CastPlaybackState(), index = 4)
        assertEquals(4, newState.pendingChannelIndex)
    }

    @Test
    fun `requestChannelSwitch overwrites a previously queued index`() {
        val first = CastReceiverStatusReducer.requestChannelSwitch(CastPlaybackState(), index = 1)
        val second = CastReceiverStatusReducer.requestChannelSwitch(first, index = 2)
        assertEquals(2, second.pendingChannelIndex)
    }

    @Test
    fun `DISCONNECTED clears idle reason back to NONE`() {
        val state = CastPlaybackState(isSessionConnected = true, idleReason = IdleReason.ERROR)
        val result = CastReceiverStatusReducer.reduce(state, ReceiverStatus.DISCONNECTED)
        assertEquals(IdleReason.NONE, result.state.idleReason)
    }
}
