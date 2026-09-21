package com.uacastplayer.playlist

/** Identifies playlist inputs whose contents may cross the network over unencrypted HTTP. */
object CleartextCredentialPolicy {
    /** Bare Xtream server addresses count as cleartext because [XtreamUrlBuilder] intentionally
     * adds `http://` before building its credential-bearing URLs. */
    fun exposesCredentials(server: String): Boolean {
        val normalized = server.trim().lowercase()
        return normalized.isNotEmpty() && !normalized.startsWith("https://")
    }

    /** Direct playlist URLs are already required to carry an explicit scheme, so warn only for a
     * real `http://` input. Invalid/bare input remains the validator's responsibility and should
     * not show two competing messages while the user is still typing. */
    fun isCleartextPlaylistUrl(url: String): Boolean =
        url.trim().startsWith("http://", ignoreCase = true)
}
