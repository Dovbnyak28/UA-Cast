package com.uacastplayer.playlist

/**
 * What to call a playlist the user never named.
 *
 * Naming is optional when a playlist is added, and the screens fell back to the source id - which
 * is a SHA-256 of the location. On a real phone that reads as **"6368ffd4"** in the largest type on
 * the home screen, under the words "Active playlist": a machine identifier presented as if it were
 * the answer to "which one is this?".
 *
 * The location itself is a better answer than the hash of it, so this derives one. It cannot always
 * find something worth showing, and says so with null rather than inventing a label - see
 * [forLocation] on why a `content://` URI is the case where it gives up.
 */
object PlaylistSourceLabel {

    /** Beyond this a name stops being a name and becomes the URL again, wrapped over three lines. */
    private const val MAX_LENGTH = 48

    /**
     * A human label for a source, or null when the location holds nothing worth showing.
     *
     * - **URL**: host plus the file name, `iptv.example.com/list.m3u`. The host alone is ambiguous
     *   for a provider serving several lists; the whole URL is unreadable and often carries
     *   credentials in a query string, which must never reach a label.
     * - **Xtream**: the server host. The rest of an Xtream URL *is* the credentials.
     * - **File**: null. A Storage Access Framework URI is opaque by design - the user's own
     *   playlist reads `content://com.android.providers.downloads.documents/document/msf%3A965`,
     *   where the last segment is a row id in the provider's database and means nothing to anybody.
     *   The real file name has to be asked of the provider (see `PlaylistFileLoader.documentName`),
     *   which needs a `ContentResolver` and can fail; this function stays pure and leaves that to
     *   the caller.
     */
    fun forLocation(type: PlaylistSourceType, location: String): String? = when (type) {
        PlaylistSourceType.XTREAM -> XtreamUrlBuilder.serverHost(location).takeIf { it.isNotBlank() }
        PlaylistSourceType.FILE -> null
        PlaylistSourceType.URL -> urlLabel(location)
    }

    private fun urlLabel(location: String): String? {
        val withoutQuery = location.substringBefore('?').substringBefore('#')
        val afterScheme = withoutQuery.substringAfter("://", withoutQuery)
        val host = afterScheme.substringBefore('/').removePrefix("www.")
        if (host.isBlank()) return null
        val fileName = afterScheme.substringAfterLast('/', "").takeIf { it.isNotBlank() && it != host }
        val label = if (fileName == null) host else "$host/$fileName"
        // Truncated from the front: the end of a URL is what distinguishes two lists on one server,
        // and the host is already visible in the group of playlists it sits with.
        return if (label.length <= MAX_LENGTH) label else "…" + label.takeLast(MAX_LENGTH - 1)
    }
}
