package com.uacastplayer.cast

/** A suspended sender still owns the remote stream, but cannot truthfully report live status. */
internal object CastSuspensionPolicy {
    const val MAX_SUSPENSION_MILLIS = 120_000L

    fun suspend(state: CastPlaybackState): CastPlaybackState = state.copy(
        isSessionConnected = true,
        isSessionSuspended = true,
        isRecovering = true,
        receiverStatus = ReceiverStatus.BUFFERING,
    )

    fun canKeepMedia(
        channelUnchanged: Boolean,
        expectedContentId: String?,
        actualContentId: String?,
        status: ReceiverStatus,
    ): Boolean = channelUnchanged && expectedContentId != null && expectedContentId == actualContentId &&
        status in setOf(ReceiverStatus.PLAYING, ReceiverStatus.PAUSED)
}
