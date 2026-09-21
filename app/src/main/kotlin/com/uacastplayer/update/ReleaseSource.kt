package com.uacastplayer.update

/**
 * Where the newest published release comes from, as an interface so
 * [com.uacastplayer.app.UpdateController] can be driven by a stub in tests rather than by the
 * network - the same reasoning as [UpdateCheckStorage].
 */
interface ReleaseSource {

    /**
     * The newest published release, or why there isn't one. See [ReleaseLookup] for why "no release
     * published yet" is a case of its own rather than a failure like any other.
     */
    suspend fun fetchLatestRelease(): ReleaseLookup
}
