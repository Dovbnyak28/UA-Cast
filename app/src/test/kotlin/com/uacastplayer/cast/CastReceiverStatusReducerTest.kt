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
    fun `PLAYING clears a previous receiver load failure`() {
        val state = CastPlaybackState(isSessionConnected = true, receiverLoadFailed = true)
        val result = CastReceiverStatusReducer.reduce(state, ReceiverStatus.PLAYING)
        assertFalse(result.state.receiverLoadFailed)
    }

    @Test
    fun `PLAYING clears a recovering flag - the reload succeeded`() {
        val state = CastPlaybackState(isSessionConnected = true, isRecovering = true)
        val result = CastReceiverStatusReducer.reduce(state, ReceiverStatus.PLAYING)
        assertFalse(result.state.isRecovering)
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
        assertTrue(result.state.receiverLoadFailed)
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

    @Test
    fun `DISCONNECTED clears a lingering codec incompatibility warning`() {
        val incompatibility = CodecIncompatibility.Video(VideoCodec.Mpeg2Video)
        val state = CastPlaybackState(isSessionConnected = true, codecIncompatibility = incompatibility)
        val result = CastReceiverStatusReducer.reduce(state, ReceiverStatus.DISCONNECTED)
        assertNull(result.state.codecIncompatibility)
    }

    @Test
    fun `DISCONNECTED clears a lingering receiver load failure`() {
        val state = CastPlaybackState(isSessionConnected = true, receiverLoadFailed = true)
        val result = CastReceiverStatusReducer.reduce(state, ReceiverStatus.DISCONNECTED)
        assertFalse(result.state.receiverLoadFailed)
    }

    @Test
    fun `DISCONNECTED clears a lingering IPv4-unavailable flag`() {
        val state = CastPlaybackState(isSessionConnected = true, proxyUnavailableIpv4Only = true)
        val result = CastReceiverStatusReducer.reduce(state, ReceiverStatus.DISCONNECTED)
        assertFalse(result.state.proxyUnavailableIpv4Only)
    }

    @Test
    fun `DISCONNECTED clears a lingering recovering flag`() {
        val state = CastPlaybackState(isSessionConnected = true, isRecovering = true)
        val result = CastReceiverStatusReducer.reduce(state, ReceiverStatus.DISCONNECTED)
        assertFalse(result.state.isRecovering)
    }

    @Test
    fun `DISCONNECTED clears a lingering likely-compatibility hint`() {
        val hint = CastCompatibilityVerdict.LikelyCompatible(audioHint = AudioCodec.MpegAudio, videoHint = null)
        val state = CastPlaybackState(isSessionConnected = true, likelyCompatibilityHint = hint)
        val result = CastReceiverStatusReducer.reduce(state, ReceiverStatus.DISCONNECTED)
        assertNull(result.state.likelyCompatibilityHint)
    }

    @Test
    fun `self-initiated IDLE with INTERRUPTED is ignored entirely`() {
        val result = CastReceiverStatusReducer.reduce(
            CastPlaybackState(),
            ReceiverStatus.IDLE,
            IdleReason.INTERRUPTED,
            selfInitiated = true,
        )
        assertTrue(result.effects.isEmpty())
        assertFalse(result.state.receiverLoadFailed)
        assertEquals(ReceiverStatus.IDLE, result.state.receiverStatus)
    }

    @Test
    fun `self-initiated IDLE with CANCELLED is ignored entirely`() {
        val result = CastReceiverStatusReducer.reduce(
            CastPlaybackState(),
            ReceiverStatus.IDLE,
            IdleReason.CANCELLED,
            selfInitiated = true,
        )
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `self-initiated IDLE with ERROR still goes through the normal error handling`() {
        val result = CastReceiverStatusReducer.reduce(
            CastPlaybackState(),
            ReceiverStatus.IDLE,
            IdleReason.ERROR,
            selfInitiated = true,
        )
        assertTrue(result.state.receiverLoadFailed)
        assertTrue(result.effects.contains(CastSideEffect.ResumeLocalPlayer))
    }

    @Test
    fun `self-initiated IDLE with FINISHED still closes the proxy session`() {
        val result = CastReceiverStatusReducer.reduce(
            CastPlaybackState(),
            ReceiverStatus.IDLE,
            IdleReason.FINISHED,
            selfInitiated = true,
        )
        assertEquals(listOf(CastSideEffect.CloseProxySession), result.effects)
    }

    @Test
    fun `a non-self-initiated IDLE with INTERRUPTED behaves as before - no special effects either way`() {
        val result = CastReceiverStatusReducer.reduce(
            CastPlaybackState(),
            ReceiverStatus.IDLE,
            IdleReason.INTERRUPTED,
            selfInitiated = false,
        )
        assertTrue(result.effects.isEmpty())
    }
}
