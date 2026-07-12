package com.uacastplayer.playlist

/** A single channel entry parsed out of an M3U playlist. */
data class M3uChannel(
    val displayName: String,
    val streamUrl: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val tvgLogo: String? = null,
    val groupTitle: String? = null,
)

data class M3uParseResult(
    val channels: List<M3uChannel>,
    val skippedLineCount: Int,
)
