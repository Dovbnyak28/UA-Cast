package com.uacastplayer.player

import androidx.media3.common.PlaybackException

/** Maps a real ExoPlayer error onto the small, retry-policy-relevant [PlaybackErrorType] set. */
object PlayerErrorClassifier {

    fun classify(exception: PlaybackException): PlaybackErrorType = when (exception.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        -> PlaybackErrorType.NETWORK

        PlaybackException.ERROR_CODE_TIMEOUT -> PlaybackErrorType.TIMEOUT

        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> PlaybackErrorType.BEHIND_LIVE_WINDOW

        else -> PlaybackErrorType.OTHER
    }
}
