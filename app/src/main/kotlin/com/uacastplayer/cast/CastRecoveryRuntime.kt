package com.uacastplayer.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Owns the entire reload episode: budget, stable-playback window, pending job and invalidation.
 * The SDK adapter only executes reloads. All calls run on the owner's Main-bound scope. */
internal class CastRecoveryRuntime(
    private val scope: CoroutineScope,
    private val activeChannel: () -> CastChannel?,
    private val reload: (CastChannel) -> Unit,
) {
    private val episode = CastRecoveryEpisode()
    private var pending: Job? = null
    private var generation = 0L

    fun onStatus(status: ReceiverStatus, nowMillis: Long): Long {
        if (status == ReceiverStatus.PLAYING) cancel()
        return episode.onStatus(status, nowMillis)
    }

    fun decisionFor(
        reason: IdleReason,
        incompatible: Boolean,
        selfInitiated: Boolean,
    ): CastRecoveryDecision = episode.decisionFor(reason, incompatible, selfInitiated)

    fun schedule(channel: CastChannel, decision: CastRecoveryDecision.Reload) {
        cancel()
        episode.scheduled(decision)
        val expectedGeneration = generation
        pending = scope.launch {
            delay(decision.backoffMillis)
            if (expectedGeneration == generation && channel == activeChannel()) reload(channel)
        }
    }

    /** Suspension keeps the budget but invalidates the delayed operation, even for A → B → A. */
    fun cancel() {
        generation++
        pending?.cancel()
        pending = null
    }

    fun reset() {
        cancel()
        episode.reset()
    }
}
