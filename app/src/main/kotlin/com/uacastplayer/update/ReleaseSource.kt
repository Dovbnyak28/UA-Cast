package com.uacastplayer.update

/**
 * Where the newest published release comes from, as an interface so
 * [com.uacastplayer.app.UpdateController] can be driven by a stub in tests rather than by the
 * network - the same reasoning as [UpdateCheckStorage].
 */
interface ReleaseSource {

    /**
     * The newest published release, or null when that could not be established - offline,
     * rate-limited, a 5xx, a draft, a tag that is not a version number. One null for every failure
     * is deliberate: every caller reacts to them identically.
     */
    suspend fun fetchLatestRelease(): GitHubRelease?
}
