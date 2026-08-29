package com.uacastplayer.playlist

/** Whether entering Xtream credentials will put them on an unencrypted HTTP connection. Bare
 * server addresses count as cleartext because [XtreamUrlBuilder] intentionally adds `http://`. */
object CleartextCredentialPolicy {
    fun exposesCredentials(server: String): Boolean {
        val normalized = server.trim().lowercase()
        return normalized.isNotEmpty() && !normalized.startsWith("https://")
    }
}
