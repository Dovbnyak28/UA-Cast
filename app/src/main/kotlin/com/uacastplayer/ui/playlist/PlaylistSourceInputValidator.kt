package com.uacastplayer.ui.playlist

import java.net.URI

/** Pure validation shared by the add-playlist form and its regression tests. */
internal object PlaylistSourceInputValidator {

    fun isValidHttpUrl(value: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        return uri.host?.isNotBlank() == true &&
            (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))
    }

    fun isValidXtream(server: String, username: String, password: String): Boolean =
        isValidXtreamServer(server) && username.isNotBlank() && password.isNotBlank()

    fun isValidXtreamServer(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        val normalized = if (trimmed.indexOf("://") > 0) trimmed else "http://$trimmed"
        return isValidHttpUrl(normalized)
    }
}
