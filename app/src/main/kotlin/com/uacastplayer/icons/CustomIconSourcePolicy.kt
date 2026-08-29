package com.uacastplayer.icons

import java.net.URI

/** Validation and resource limits for user-provided icon CDN base URLs. */
object CustomIconSourcePolicy {

    const val MAX_SOURCES = 16
    const val MAX_URL_LENGTH = 2_048

    /** Returns a canonical base URL, or null when the input is not a safe HTTP(S) base. */
    fun canonicalize(raw: String): String? {
        val value = raw.trim()
        val uri = value
            .takeIf { it.isNotEmpty() && it.length <= MAX_URL_LENGTH }
            ?.let { runCatching { URI(it) }.getOrNull() }
            ?.takeIf { candidate ->
                candidate.isAbsolute &&
                    candidate.userInfo == null &&
                    !candidate.host.isNullOrBlank() &&
                    candidate.port in -1..MAX_TCP_PORT &&
                    (candidate.scheme.equals("http", ignoreCase = true) ||
                        candidate.scheme.equals("https", ignoreCase = true)) &&
                    candidate.query == null &&
                    candidate.fragment == null
            }
        val normalized = uri?.normalize()?.toString()
        val schemeSeparator = normalized?.indexOf(':') ?: -1
        val withLowercaseScheme = if (normalized != null && schemeSeparator > 0) {
            normalized.substring(0, schemeSeparator).lowercase() + normalized.substring(schemeSeparator)
        } else {
            normalized
        }
        return withLowercaseScheme?.trimEnd('/')
    }

    private const val MAX_TCP_PORT = 65_535
}
