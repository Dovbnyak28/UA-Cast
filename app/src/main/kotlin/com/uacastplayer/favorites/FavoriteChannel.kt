package com.uacastplayer.favorites

import com.uacastplayer.playlist.M3uChannel

data class FavoriteChannel(
    val key: String,
    val displayName: String,
    val streamUrl: String,
    val tvgId: String?,
    val groupTitle: String?,
    /** Wall-clock time this was favorited; 0L for favorites saved before this field existed. */
    val addedAtMillis: Long = 0L,
    val tvgName: String? = null,
    val tvgLogo: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
) {
    fun toChannel(): M3uChannel = M3uChannel(
        displayName = displayName, streamUrl = streamUrl, tvgId = tvgId,
        tvgName = tvgName, tvgLogo = tvgLogo, groupTitle = groupTitle,
        userAgent = userAgent, referrer = referrer,
    )
}
