package com.uacastplayer.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SUSTAINED_BUFFERING_TIMEOUT_MILLIS = 15_000L

internal data class CastWatchdogTiming(
    val sustainedBufferingMillis: Long = SUSTAINED_BUFFERING_TIMEOUT_MILLIS,
    val stallTickMillis: Long = CastStallWatchdogPolicy.TICK_MILLIS,
)

internal sealed interface CastWatchdogFailure {
    data class SustainedBuffering(
        val timeoutMillis: Long,
        val deliveryMode: CastDeliveryMode,
    ) : CastWatchdogFailure

    data class LoadStall(
        val elapsedMillis: Long,
        val bytesDeliveredThisTick: Long,
        val receiverStatus: ReceiverStatus,
        val deliveryMode: CastDeliveryMode,
    ) : CastWatchdogFailure
}

internal class CastWatchdogInputs(
    val currentGeneration: () -> Long,
    val activeStreamUrl: () -> String?,
    val receiverStatus: () -> ReceiverStatus,
    val deliveryMode: () -> CastDeliveryMode,
    val everReachedPlaying: () -> Boolean,
    val bytesServedToReceiver: () -> Long,
)

/**
 * Owns Cast playback timers independently of the Google Cast SDK adapter.
 *
 * The repository translates SDK callbacks into [ReceiverStatus] and performs the eventual reload;
 * this class only decides which timer should exist, guards it against stale loads/channels, and
 * reports one synthetic failure. Keeping both timers together prevents them from racing each
 * other: a real IDLE cancels the silent-load timer, while sustained-buffering recovery is armed
 * only after this channel has actually reached PLAYING once.
 */
internal class CastPlaybackWatchdogs(
    private val scope: CoroutineScope,
    private val inputs: CastWatchdogInputs,
    private val onFailure: (CastWatchdogFailure) -> Unit,
    private val timing: CastWatchdogTiming = CastWatchdogTiming(),
) {

    private var sustainedBufferingJob: Job? = null
    private var stallWatchdogJob: Job? = null

    /** Must be called before the repository reduces the matching SDK callback. Providers are read
     * again only after a delay, when state already reflects that callback. */
    fun onReceiverStatus(status: ReceiverStatus) {
        when (status) {
            ReceiverStatus.BUFFERING -> {
                if (inputs.everReachedPlaying()) scheduleSustainedBuffering() else sustainedBufferingJob?.cancel()
            }
            // PLAYING and PAUSED both prove that the media loaded. IDLE is a real terminal signal
            // and drives recovery itself; leaving the synthetic stall timer alive would schedule
            // the same failure a second time during recovery backoff.
            ReceiverStatus.PLAYING,
            ReceiverStatus.PAUSED,
            ReceiverStatus.IDLE,
            ReceiverStatus.DISCONNECTED,
            -> {
                sustainedBufferingJob?.cancel()
                stallWatchdogJob?.cancel()
            }
        }
    }

    fun watchLoad(generation: Long, streamUrl: String) {
        stallWatchdogJob?.cancel()
        stallWatchdogJob = scope.launch {
            var elapsed = 0L
            var previousBytes = inputs.bytesServedToReceiver()
            while (true) {
                delay(timing.stallTickMillis)
                elapsed += timing.stallTickMillis
                if (generation != inputs.currentGeneration()) return@launch
                if (!StaleChannelGuard.isCurrent(streamUrl, inputs.activeStreamUrl())) return@launch
                val bytes = inputs.bytesServedToReceiver()
                val delivered = bytes - previousBytes
                previousBytes = bytes
                val status = inputs.receiverStatus()
                when (
                    CastStallWatchdogPolicy.decide(
                        elapsedMillis = elapsed,
                        bytesDeliveredThisTick = delivered,
                        isPlaying = status == ReceiverStatus.PLAYING,
                    )
                ) {
                    CastStallDecision.Settled -> return@launch
                    CastStallDecision.KeepWaiting -> Unit
                    CastStallDecision.Fire -> {
                        sustainedBufferingJob?.cancel()
                        onFailure(
                            CastWatchdogFailure.LoadStall(
                                elapsedMillis = elapsed,
                                bytesDeliveredThisTick = delivered,
                                receiverStatus = status,
                                deliveryMode = inputs.deliveryMode(),
                            ),
                        )
                        return@launch
                    }
                }
            }
        }
    }

    fun cancelAll() {
        sustainedBufferingJob?.cancel()
        stallWatchdogJob?.cancel()
    }

    private fun scheduleSustainedBuffering() {
        if (sustainedBufferingJob?.isActive == true) return
        val generation = inputs.currentGeneration()
        val streamUrl = inputs.activeStreamUrl() ?: return
        sustainedBufferingJob = scope.launch {
            delay(timing.sustainedBufferingMillis)
            if (generation != inputs.currentGeneration()) return@launch
            if (inputs.receiverStatus() != ReceiverStatus.BUFFERING) return@launch
            if (!StaleChannelGuard.isCurrent(streamUrl, inputs.activeStreamUrl())) return@launch
            stallWatchdogJob?.cancel()
            onFailure(
                CastWatchdogFailure.SustainedBuffering(
                    timeoutMillis = timing.sustainedBufferingMillis,
                    deliveryMode = inputs.deliveryMode(),
                ),
            )
        }
    }

}
