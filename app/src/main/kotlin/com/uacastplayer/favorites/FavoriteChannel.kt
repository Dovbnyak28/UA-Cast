package com.uacastplayer.favorites

data class FavoriteChannel(
    val key: String,
    val displayName: String,
    val streamUrl: String,
    val tvgId: String?,
    val groupTitle: String?,
)
