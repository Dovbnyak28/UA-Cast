package com.uacastplayer.playlist

sealed class PlaylistLoadResult {
    data class Success(val text: String) : PlaylistLoadResult()
    data object SizeLimitExceeded : PlaylistLoadResult()
    data class HttpError(val code: Int) : PlaylistLoadResult()
    data class ReadError(val message: String?) : PlaylistLoadResult()
}
