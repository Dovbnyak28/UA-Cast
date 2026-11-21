package com.uacastplayer.playlist

import java.net.URI
import java.net.URISyntaxException

/** A byte-bounded playlist can still contain millions of comma-separated metadata values. */
internal class M3uEpgUrls(private val checkCancellation: () -> Unit) {
    private val urls = linkedSetOf<String>()

    fun add(value: String) {
        var start = 0
        for (end in 0..value.length) {
            if (end % CHECK_INTERVAL == 0) checkCancellation()
            if (end == value.length || value[end] == ',') {
                if (urls.size == MAX_URLS) return
                addCandidate(value, start, end)
                start = end + 1
            }
        }
    }

    fun result(): List<String> = urls.toList()

    private fun addCandidate(value: String, from: Int, until: Int) {
        var start = from
        var end = until
        // At most one substring per bounded candidate; never materialize an oversized field.
        while (start < end && value[start].isWhitespace()) start++
        while (end > start && value[end - 1].isWhitespace()) end--
        val length = end - start
        if (length !in MIN_URL_LENGTH..MAX_URL_LENGTH) return
        if (!value.regionMatches(start, "http://", 0, HTTP_PREFIX_LENGTH, true) &&
            !value.regionMatches(start, "https://", 0, HTTPS_PREFIX_LENGTH, true)
        ) return
        val candidate = value.substring(start, end)
        try {
            if (!URI(candidate).rawAuthority.isNullOrBlank()) urls.add(candidate)
        } catch (_: URISyntaxException) {
            // A bad optional EPG URL must not prevent loading otherwise usable channels.
        }
    }

    companion object {
        const val MAX_URLS = 16
        const val MAX_URL_LENGTH = 4_096
        private const val CHECK_INTERVAL = 1_024
        private const val HTTP_PREFIX_LENGTH = 7
        private const val HTTPS_PREFIX_LENGTH = 8
        private const val MIN_URL_LENGTH = HTTP_PREFIX_LENGTH + 1
    }
}
