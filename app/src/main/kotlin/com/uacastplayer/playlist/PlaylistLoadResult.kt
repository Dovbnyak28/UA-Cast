package com.uacastplayer.playlist

sealed interface PlaylistLoadResult {
    data class Success(val text: String) : PlaylistLoadResult
    data object SizeLimitExceeded : PlaylistLoadResult
    data class HttpError(val code: Int) : PlaylistLoadResult

    /**
     * @param message the failing exception's class name (`e.javaClass.simpleName`), never
     *   `e.message` - see [com.uacastplayer.data.epg.EpgDownloader]'s `ReadError` for the same
     *   rule stated first. `IOException.message` on a network failure routinely echoes the request
     *   URL back (`UnknownHostException: panel.iptv-provider.com`, or worse), and this app's own
     *   playlist URLs commonly carry an Xtream username/password as query parameters. Nothing
     *   downstream shows this field on screen today - `PlaylistOutcomeReducer` discards it - but a
     *   value type is not the place to store a credential-bearing string on the strength of "nobody
     *   reads it yet".
     */
    data class ReadError(val message: String?) : PlaylistLoadResult
}
