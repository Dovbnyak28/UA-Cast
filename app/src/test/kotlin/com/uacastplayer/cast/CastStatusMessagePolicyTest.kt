package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastStatusMessagePolicyTest {

    private val ac3Hint = CastCompatibilityVerdict.LikelyCompatible(audioHint = AudioCodec.Ac3, videoHint = null)
    private val hevcHint = CastCompatibilityVerdict.LikelyCompatible(audioHint = null, videoHint = VideoCodec.Hevc)
    private val mpeg2 = CodecIncompatibility.Video(VideoCodec.Mpeg2Video)

    private fun message(state: CastPlaybackState) = CastStatusMessagePolicy.messageFor(state)

    /**
     * The regression this whole policy exists for, reproduced from a real session: an AC-3 channel
     * on a Chromecast that accepted every load and rendered nothing. Recovery stays true forever
     * (CastRecoveryPolicy only gives up on a confirmed MPEG-2 verdict), so with the recovering
     * branch ahead of the hints the user watched "Restoring cast..." indefinitely while the app
     * held the exact cause and printed the same codec a few dp below as channel details.
     */
    @Test
    fun `a retrying cast that has never played is explained, not called recovering`() {
        assertEquals(
            CastStatusMessage.LikelyIncompatibleAudio(AudioCodec.Ac3),
            message(
                CastPlaybackState(
                    isRecovering = true,
                    recoveringWithoutPlayback = true,
                    likelyCompatibilityHint = ac3Hint,
                ),
            ),
        )
    }

    /** The other half of the same distinction: a receiver that played and then dropped really is
     * recovering, and must not be told it has a codec problem. */
    @Test
    fun `a retrying cast that did play stays recovering`() {
        assertEquals(
            CastStatusMessage.Recovering,
            message(
                CastPlaybackState(
                    isRecovering = true,
                    recoveringWithoutPlayback = false,
                    likelyCompatibilityHint = ac3Hint,
                ),
            ),
        )
    }

    @Test
    fun `a confirmed incompatibility outranks everything else`() {
        assertEquals(
            CastStatusMessage.IncompatibleVideo(VideoCodec.Mpeg2Video),
            message(
                CastPlaybackState(
                    codecIncompatibility = mpeg2,
                    isRecovering = true,
                    recoveringWithoutPlayback = true,
                    receiverLoadFailed = true,
                    likelyCompatibilityHint = ac3Hint,
                ),
            ),
        )
    }

    /** Matches CastCompatibilityPolicy's own priority - a video hint is the likelier culprit. */
    @Test
    fun `a video hint is preferred over an audio hint`() {
        val both = CastCompatibilityVerdict.LikelyCompatible(AudioCodec.Ac3, VideoCodec.Hevc)
        assertEquals(
            CastStatusMessage.LikelyIncompatibleVideo(VideoCodec.Hevc),
            message(CastPlaybackState(receiverLoadFailed = true, likelyCompatibilityHint = both)),
        )
    }

    @Test
    fun `a failed load with no hint falls back to the generic message`() {
        assertEquals(
            CastStatusMessage.ReceiverLoadFailed,
            message(CastPlaybackState(receiverLoadFailed = true)),
        )
    }

    @Test
    fun `a hint alone says nothing - it only ever qualifies a failure`() {
        assertNull(message(CastPlaybackState(likelyCompatibilityHint = hevcHint)))
        assertNull(message(CastPlaybackState(likelyCompatibilityHint = ac3Hint)))
    }

    @Test
    fun `a healthy cast shows no message`() {
        assertNull(message(CastPlaybackState()))
    }

    @Test
    fun `an IPv4-only proxy failure is named rather than left generic`() {
        assertEquals(
            CastStatusMessage.ProxyUnavailableIpv4Only,
            message(
                CastPlaybackState(
                    proxyUnavailableIpv4Only = true,
                    receiverLoadFailed = true,
                    likelyCompatibilityHint = ac3Hint,
                ),
            ),
        )
    }

    /** Every message kind must be reachable. Written as a sweep because the defect being fixed was
     * precisely a branch that could never be selected, not one that returned the wrong value. */
    @Test
    fun `every message kind is reachable from some input`() {
        val produced = setOf(
            message(CastPlaybackState(codecIncompatibility = mpeg2)),
            message(CastPlaybackState(isRecovering = true)),
            message(CastPlaybackState(proxyUnavailableIpv4Only = true, receiverLoadFailed = true)),
            message(CastPlaybackState(receiverLoadFailed = true, likelyCompatibilityHint = hevcHint)),
            message(CastPlaybackState(receiverLoadFailed = true, likelyCompatibilityHint = ac3Hint)),
            message(CastPlaybackState(receiverLoadFailed = true)),
        )
        assertEquals("each input must produce a distinct message", 6, produced.size)
    }
}

class CastRecoveringWithoutPlaybackTest {

    private fun check(
        everReachedPlaying: Boolean = false,
        deliveryMode: CastDeliveryMode = CastDeliveryMode.Proxy,
        attempt: Int = CastRecoveryPolicy.MAX_ATTEMPTS,
    ) = CastStatusMessagePolicy.isRecoveringWithoutPlayback(everReachedPlaying, deliveryMode, attempt)

    @Test
    fun `never played, on the proxy, fast attempts spent`() {
        assertTrue(check())
    }

    /** Anything that has played is recovering toward something real, however briefly it managed it. */
    @Test
    fun `having played at all keeps it a recovery`() {
        assertFalse(check(everReachedPlaying = true))
    }

    /** On Direct the proxy fallback has not been tried yet - there is still a route that may work,
     * so calling it hopeless would be premature. */
    @Test
    fun `still on the direct route is not hopeless yet`() {
        assertFalse(check(deliveryMode = CastDeliveryMode.Direct))
    }

    @Test
    fun `the fast attempts are allowed to finish first`() {
        for (attempt in 1 until CastRecoveryPolicy.MAX_ATTEMPTS) {
            assertFalse("attempt $attempt is still a fast retry", check(attempt = attempt))
        }
        assertTrue(check(attempt = CastRecoveryPolicy.MAX_ATTEMPTS))
        assertTrue(check(attempt = CastRecoveryPolicy.MAX_ATTEMPTS + 5))
    }
}
