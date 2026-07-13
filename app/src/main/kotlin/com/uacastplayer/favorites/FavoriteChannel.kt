package com.uacastplayer.favorites

data class FavoriteChannel(
    val key: String,
    val displayName: String,
    val streamUrl: String,
    val tvgId: String?,
    val groupTitle: String?,
    /** Wall-clock time this was favorited; 0L for favorites saved before this field existed. */
    val addedAtMillis: Long = 0L,
)
