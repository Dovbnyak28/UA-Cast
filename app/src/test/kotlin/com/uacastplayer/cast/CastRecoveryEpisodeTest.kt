package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class CastRecoveryEpisodeTest {

    @Test
    fun `scheduled attempts advance without any Cast SDK dependency`() {
        val episode = CastRecoveryEpisode()
        val first = episode.decisionFor(IdleReason.ERROR, isConfirmedIncompatible = false, selfInitiated = false)
        episode.scheduled(first as CastRecoveryDecision.Reload)

        val second = episode.decisionFor(IdleReason.ERROR, isConfirmedIncompatible = false, selfInitiated = false)

        assertEquals(CastRecoveryDecision.Reload(attempt = 2, backoffMillis = 4_000L), second)
    }

    @Test
    fun `a stable playing window gives the next failure a fresh attempt budget`() {
        val episode = CastRecoveryEpisode()
        val first = episode.decisionFor(IdleReason.ERROR, isConfirmedIncompatible = false, selfInitiated = false)
        episode.scheduled(first as CastRecoveryDecision.Reload)
        episode.onStatus(ReceiverStatus.PLAYING, nowMillis = 1_000L)

        val stableMillis = episode.onStatus(
            ReceiverStatus.IDLE,
            nowMillis = 1_000L + CastRecoveryPolicy.STABLE_PLAYING_RESET_MILLIS,
        )
        val afterRecovery = episode.decisionFor(
            IdleReason.ERROR,
            isConfirmedIncompatible = false,
            selfInitiated = false,
        )

        assertEquals(CastRecoveryPolicy.STABLE_PLAYING_RESET_MILLIS, stableMillis)
        assertEquals(CastRecoveryDecision.Reload(attempt = 1, backoffMillis = 2_000L), afterRecovery)
    }

    @Test
    fun `reset starts a new channel episode`() {
        val episode = CastRecoveryEpisode()
        val first = episode.decisionFor(IdleReason.ERROR, isConfirmedIncompatible = false, selfInitiated = false)
        episode.scheduled(first as CastRecoveryDecision.Reload)

        episode.reset()

        assertEquals(
            CastRecoveryDecision.Reload(attempt = 1, backoffMillis = 2_000L),
            episode.decisionFor(IdleReason.ERROR, isConfirmedIncompatible = false, selfInitiated = false),
        )
    }
}
