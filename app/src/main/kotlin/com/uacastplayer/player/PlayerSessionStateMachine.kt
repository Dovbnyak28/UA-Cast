package com.uacastplayer.player

import com.uacastplayer.playlist.M3uChannel

/**
 * Pure session state for channel navigation and playback recovery.
 *
 * [PlayerViewModel] remains the Media3/Cast/DLNA adapter: it supplies observations and executes the
 * effects returned here. Keeping the mutable recovery budgets in this class makes their lifetime
 * explicit - every per-channel budget is reset by [switchTo], while a new [start] also clears
 * playlist-wide state such as dead channels and previous-channel history.
 */
internal class PlayerSessionStateMachine {

    data class ChannelSwitch(
        val index: Int,
        val channel: M3uChannel,
        val preview: List<IndexedChannel>,
        val hasPreviousChannel: Boolean,
    )

    sealed interface PlaybackFailureEffect {
        data class RecoverLiveWindow(val attemptInWindow: Int) : PlaybackFailureEffect
        data class Retry(val delayMillis: Long) : PlaybackFailureEffect
        data object RetryWhenNetworkAvailable : PlaybackFailureEffect
        data class SwitchChannel(
            val transition: ChannelSwitch,
            val skippedChannels: Int,
            val totalChannels: Int,
        ) : PlaybackFailureEffect
        data object Fatal : PlaybackFailureEffect
    }

    sealed interface StallEffect {
        data object None : StallEffect
        data object ClearRecoveryIndicator : StallEffect
        data class ScheduleRecovery(val delayMillis: Long, val attempt: Int) : StallEffect
    }

    private var channels: List<M3uChannel> = emptyList()
    private var currentIndex: Int = -1
    private val deadIndices = mutableSetOf<Int>()
    private var retryState = RetryState()
    private var liveWindowRecoveryHistory: List<Long> = emptyList()
    private var stallState = StallDetectionPolicy.StallState.NONE
    private var stallRetryState = StallRetryPolicy.State()
    private var channelHistory = ChannelHistoryPolicy.State(current = null, previous = null)

    val hasCurrentChannel: Boolean get() = currentIndex in channels.indices
    val previousChannelIndex: Int? get() = channelHistory.previous?.takeIf { it in channels.indices }

    fun start(
        channels: List<M3uChannel>,
        startIndex: Int,
        wrapAround: Boolean,
    ): ChannelSwitch? {
        this.channels = channels
        currentIndex = -1
        deadIndices.clear()
        channelHistory = ChannelHistoryPolicy.State(current = null, previous = null)
        resetPerChannelRecovery()
        return switchTo(startIndex, wrapAround)
    }

    fun release() {
        channels = emptyList()
        currentIndex = -1
        deadIndices.clear()
        channelHistory = ChannelHistoryPolicy.State(current = null, previous = null)
        resetPerChannelRecovery()
    }

    fun nextIndex(wrapAround: Boolean): Int? =
        ChannelNavigator.nextIndex(currentIndex, channels.size, wrapAround)

    fun previousIndex(wrapAround: Boolean): Int? =
        ChannelNavigator.previousIndex(currentIndex, channels.size, wrapAround)

    fun switchTo(index: Int, wrapAround: Boolean): ChannelSwitch? {
        if (index !in channels.indices) return null
        channelHistory = ChannelHistoryPolicy.onSwitch(channelHistory, index)
        currentIndex = index
        resetPerChannelRecovery()
        return channelSwitch(wrapAround)
    }

    /** Explicit retry from the error UI gives the current channel a fresh budget. */
    fun retryCurrent(wrapAround: Boolean): ChannelSwitch? {
        if (!hasCurrentChannel) return null
        deadIndices.remove(currentIndex)
        resetPerChannelRecovery()
        return channelSwitch(wrapAround)
    }

    fun onPlaybackConfirmed() {
        retryState = PlaybackRetryPolicy.onIsPlaying(retryState)
        liveWindowRecoveryHistory = emptyList()
    }

    /** A pending stall recovery became obsolete because playback resumed, was paused, or moved to
     * a remote receiver. It must restart at attempt one if local playback later stalls again. */
    fun cancelStallRecovery() {
        stallState = StallDetectionPolicy.StallState.NONE
        stallRetryState = StallRetryPolicy.State()
    }

    fun onPlaybackError(
        errorType: PlaybackErrorType,
        nowMillis: Long,
        hasNetwork: Boolean,
        autoSkipDead: Boolean,
        wrapAround: Boolean,
    ): PlaybackFailureEffect {
        if (errorType == PlaybackErrorType.BEHIND_LIVE_WINDOW) {
            when (val decision = LiveWindowRecoveryPolicy.onBehindLiveWindow(nowMillis, liveWindowRecoveryHistory)) {
                is LiveWindowRecoveryPolicy.Decision.Recover -> {
                    liveWindowRecoveryHistory = decision.newHistory
                    return PlaybackFailureEffect.RecoverLiveWindow(decision.newHistory.size)
                }
                LiveWindowRecoveryPolicy.Decision.GiveUp -> Unit
            }
        }

        return when (val decision = PlaybackRetryPolicy.onError(retryState, errorType)) {
            is RetryDecision.Retry -> {
                retryState = decision.newState
                PlaybackFailureEffect.Retry(decision.delayMillis)
            }
            RetryDecision.GiveUp -> onRetryBudgetExhausted(hasNetwork, autoSkipDead, wrapAround)
        }
    }

    fun onStallTick(
        tick: StallDetectionPolicy.Tick,
        thresholdMillis: Long,
    ): StallEffect {
        val result = StallDetectionPolicy.evaluate(tick, stallState, thresholdMillis)
        stallState = result.state
        if (result.health != StallDetectionPolicy.Health.STALLED) {
            return if (result.inGracePeriod) StallEffect.None else StallEffect.ClearRecoveryIndicator
        }

        val decision = StallRetryPolicy.onStall(tick.nowMillis, stallRetryState)
        stallRetryState = decision.newState
        // Arm the grace period before the delayed effect is executed so subsequent samples cannot
        // schedule overlapping recoveries for the same stalled interval.
        stallState = StallDetectionPolicy.afterRecovery(tick.nowMillis, thresholdMillis)
        return StallEffect.ScheduleRecovery(
            delayMillis = decision.delayMillis,
            attempt = decision.newState.attempt,
        )
    }

    private fun onRetryBudgetExhausted(
        hasNetwork: Boolean,
        autoSkipDead: Boolean,
        wrapAround: Boolean,
    ): PlaybackFailureEffect {
        if (!DeadChannelPolicy.shouldBlameChannel(hasNetwork)) {
            retryState = RetryState()
            return PlaybackFailureEffect.RetryWhenNetworkAvailable
        }

        deadIndices += currentIndex
        val next = if (autoSkipDead) {
            ChannelNavigator.nextPlayableIndex(currentIndex, channels.size, wrapAround) { it in deadIndices }
        } else {
            null
        }
        return next?.let { switchTo(it, wrapAround) }
            ?.let { transition ->
                PlaybackFailureEffect.SwitchChannel(
                    transition = transition,
                    skippedChannels = deadIndices.size,
                    totalChannels = channels.size,
                )
            }
            ?: PlaybackFailureEffect.Fatal
    }

    private fun channelSwitch(wrapAround: Boolean): ChannelSwitch {
        val preview = mutableListOf<IndexedChannel>()
        var previewIndex = currentIndex
        repeat(minOf(MAX_PREVIEW_SIZE, channels.size - 1)) {
            val next = ChannelNavigator.nextIndex(previewIndex, channels.size, wrapAround)
                ?: return@repeat
            preview += IndexedChannel(next, channels[next])
            previewIndex = next
        }
        return ChannelSwitch(
            index = currentIndex,
            channel = channels[currentIndex],
            preview = preview,
            hasPreviousChannel = previousChannelIndex != null,
        )
    }

    private fun resetPerChannelRecovery() {
        retryState = RetryState()
        liveWindowRecoveryHistory = emptyList()
        cancelStallRecovery()
    }

    private companion object {
        const val MAX_PREVIEW_SIZE = 20
    }
}
