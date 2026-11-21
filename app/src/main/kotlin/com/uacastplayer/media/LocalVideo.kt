package com.uacastplayer.media

import java.net.URI

/** Metadata only; file bytes and Android permission/descriptor ownership stay in data/media. */
data class LocalVideo(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val canRestore: Boolean = false,
) {
    init {
        require(uri.length <= MAX_URI_LENGTH && mimeType.length <= MAX_MIME_LENGTH)
        val parsed = URI(uri)
        require(parsed.scheme.equals("content", true) && !parsed.authority.isNullOrBlank())
        require(parsed.userInfo == null && parsed.fragment == null)
        require(displayName.isNotBlank() && displayName.length <= MAX_NAME_LENGTH)
        require(mimeType.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")))
    }

    companion object {
        const val MAX_URI_LENGTH = 4096
        const val MAX_NAME_LENGTH = 200
        const val MAX_MIME_LENGTH = 127
    }
}

object LocalVideoFormats {
    private val videoMime = Regex("video/[a-z0-9.+-]+")
    private val playlistTypes = setOf("application/vnd.apple.mpegurl", "application/x-mpegurl",
        "audio/mpegurl", "audio/x-mpegurl", "video/mpegurl", "video/x-mpegurl", "video/vnd.apple.mpegurl")
    private val types = mapOf(
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mov" to "video/quicktime",
        "mkv" to "video/x-matroska", "webm" to "video/webm", "avi" to "video/x-msvideo",
        "ts" to "video/mp2t", "mts" to "video/mp2t", "m2ts" to "video/mp2t",
        "mpeg" to "video/mpeg", "mpg" to "video/mpeg", "vob" to "video/mpeg",
        "3gp" to "video/3gpp", "3g2" to "video/3gpp2", "flv" to "video/x-flv",
        "wmv" to "video/x-ms-wmv", "asf" to "video/x-ms-asf", "ogv" to "video/ogg",
        "mxf" to "application/mxf", "rm" to "application/vnd.rn-realmedia",
        "rmvb" to "application/vnd.rn-realmedia-vbr",
    )
    private val legacyMime = types.values.filter { it.startsWith("application/") }.toSet()

    /** Selection support is not a promise that every device/receiver can decode this codec. */
    fun mimeType(name: String, declared: String?): String? {
        val clean = declared?.substringBefore(';')?.trim()?.lowercase()
            ?.takeIf { it.length <= LocalVideo.MAX_MIME_LENGTH }
        if (clean in playlistTypes) return null
        val concrete = clean?.takeIf { it.matches(videoMime) || it in legacyMime }
        return concrete ?: types[name.substringAfterLast('.', "").lowercase()]
    }
}
