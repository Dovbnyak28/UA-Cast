package com.uacastplayer.playlist

import java.net.URI
import java.net.URLEncoder

/**
 * Builds the M3U/XMLTV URLs an Xtream Codes panel expects from separate server/username/password
 * fields - most providers hand these three out instead of a ready-made M3U URL. The resulting URL
 * feeds straight into the normal PlaylistUrlLoader pipeline unchanged; nothing about Xtream is
 * special beyond how this one URL gets built. Credentials are URL-encoded since Xtream passwords
 * commonly contain symbols that would otherwise break the query string.
 */
object XtreamUrlBuilder {
    fun playlistUrl(server: String, username: String, password: String): String =
        "${normalizeServer(server)}/get.php?${credentials(username, password)}&type=m3u_plus&output=ts"

    /** Same server/credentials, Xtream's XMLTV endpoint - fed into EpgSourceAutoDetect as a found
     * EPG URL alongside whatever (if anything) the M3U's own #EXTM3U header advertises. */
    fun epgUrl(server: String, username: String, password: String): String =
        "${normalizeServer(server)}/xmltv.php?${credentials(username, password)}"

    /** Default display name for an Xtream source - just the server's host, without scheme/port/
     * credentials cluttering the label. Falls back to the raw input if it doesn't parse as a URI
     * at all (a genuinely malformed server address will fail the actual load anyway). */
    fun serverHost(server: String): String {
        val normalized = normalizeServer(server)
        return runCatching { URI(normalized).host }.getOrNull() ?: server.trim()
    }

    /** Adds `http://` when no scheme is present (Xtream panels are commonly quoted as a bare
     * host:port); strips a trailing slash so the path appended above can't end up with `//`. */
    private fun normalizeServer(server: String): String {
        val trimmed = server.trim().trimEnd('/')
        return if (trimmed.contains("://")) trimmed else "http://$trimmed"
    }

    private fun credentials(username: String, password: String): String =
        "username=${encode(username)}&password=${encode(password)}"

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
