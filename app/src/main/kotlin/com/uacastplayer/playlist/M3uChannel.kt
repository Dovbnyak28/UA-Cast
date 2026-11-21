package com.uacastplayer.playlist

import com.uacastplayer.media.LocalVideo

/** A single channel entry parsed out of an M3U playlist. */
data class M3uChannel(
    val displayName: String,
    val streamUrl: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val tvgLogo: String? = null,
    val groupTitle: String? = null,
    /** From a preceding `#EXTVLCOPT:http-user-agent=`/`http-referrer=` line, if the playlist has one. */
    val userAgent: String? = null,
    val referrer: String? = null,
    /** Explicitly selected document, never populated by a downloaded playlist. */
    val localVideo: LocalVideo? = null,
)

data class M3uParseResult(
    val channels: List<M3uChannel>,
    val skippedLineCount: Int,
    /** From the `#EXTM3U` header's `url-tvg`/`x-tvg-url` attribute, if present - see
     * [com.uacastplayer.epg.EpgSourceAutoDetect] for what happens with these. */
    val epgUrls: List<String> = emptyList(),
    /** True when the input contained more channels than [M3uParser.MAX_CHANNELS]. */
    val channelLimitExceeded: Boolean = false,
)
