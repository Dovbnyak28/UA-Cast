package com.uacastplayer.player

internal enum class LocalPlaybackRetryAction {
    PREPARE_NOW,
    DEFER_UNTIL_FOREGROUND,
    DROP,
}

/**
 * Revalidates a delayed local retry at execution time. A retry can wait for network or backoff for
 * many seconds; by then playback may belong to a receiver, the app may be off screen, or the user
 * may have paused. Calling `prepare()` from any of those stale states opens an unwanted IPTV
 * connection and can starve a one-connection-per-account cast.
 */
internal object LocalPlaybackRetryPolicy {
    fun decide(
        isRemoteCasting: Boolean,
        isInBackground: Boolean,
        wantsToPlay: Boolean,
        pausedForBackground: Boolean,
    ): LocalPlaybackRetryAction = when {
        isRemoteCasting -> LocalPlaybackRetryAction.DROP
        !wantsToPlay && !pausedForBackground -> LocalPlaybackRetryAction.DROP
        isInBackground -> LocalPlaybackRetryAction.DEFER_UNTIL_FOREGROUND
        else -> LocalPlaybackRetryAction.PREPARE_NOW
    }
}
