package com.uacastplayer.epg

data class EpgChannel(
    val id: String,
    val displayNames: List<String>,
    val iconUrl: String?,
)

data class EpgProgramme(
    val channelId: String,
    val startMillis: Long,
    val stopMillis: Long,
    val title: String,
    val description: String?,
)
