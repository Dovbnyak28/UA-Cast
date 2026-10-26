package com.uacastplayer.cast

import com.google.android.gms.cast.MediaStatus

internal fun mapPlayerState(playerState: Int): ReceiverStatus = when (playerState) {
    MediaStatus.PLAYER_STATE_PLAYING -> ReceiverStatus.PLAYING
    MediaStatus.PLAYER_STATE_PAUSED -> ReceiverStatus.PAUSED
    MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> ReceiverStatus.BUFFERING
    else -> ReceiverStatus.IDLE
}

internal fun mapIdleReason(reason: Int): IdleReason = when (reason) {
    MediaStatus.IDLE_REASON_FINISHED -> IdleReason.FINISHED
    MediaStatus.IDLE_REASON_ERROR -> IdleReason.ERROR
    MediaStatus.IDLE_REASON_CANCELED -> IdleReason.CANCELLED
    MediaStatus.IDLE_REASON_INTERRUPTED -> IdleReason.INTERRUPTED
    else -> IdleReason.NONE
}

internal fun bufferedVideoFinished(
    channel: CastChannel?, status: ReceiverStatus, reason: IdleReason,
): Boolean {
    val finished = status == ReceiverStatus.IDLE && reason == IdleReason.FINISHED
    return channel?.localVideo != null && finished
}

/** Classifies a nullable SDK snapshot without adding lifecycle policy to the session adapter. */
internal fun bufferedVideoFinished(channel: CastChannel?, status: MediaStatus?): Boolean = status?.let {
    bufferedVideoFinished(channel, mapPlayerState(it.playerState), mapIdleReason(it.idleReason))
} ?: false

internal fun CastPlaybackState.withBufferedVideoFinished(): CastPlaybackState = copy(
    isSessionConnected = true,
    loadPhase = CastLoadPhase.LOADED,
    receiverStatus = ReceiverStatus.IDLE, idleReason = IdleReason.FINISHED,
    isRecovering = false, recoveringWithoutPlayback = false,
)
