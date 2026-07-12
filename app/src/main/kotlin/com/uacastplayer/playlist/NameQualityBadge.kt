package com.uacastplayer.playlist

/**
 * Detects an "honest" quality badge for a channel list row: only an explicit marker already
 * present in the channel's own name (as providers commonly append), never a guess based on
 * anything else. Checked in descending order of quality so "4K UHD Sports" doesn't undersell
 * itself as HD.
 */
object NameQualityBadge {

    private val markers = listOf(
        "4K" to Regex("""\b4K\b""", RegexOption.IGNORE_CASE),
        "UHD" to Regex("""\bUHD\b""", RegexOption.IGNORE_CASE),
        "FHD" to Regex("""\bFHD\b""", RegexOption.IGNORE_CASE),
        "HD" to Regex("""\bHD\b""", RegexOption.IGNORE_CASE),
        "SD" to Regex("""\bSD\b""", RegexOption.IGNORE_CASE),
    )

    fun detect(channelName: String): String? =
        markers.firstOrNull { (_, regex) -> regex.containsMatchIn(channelName) }?.first
}
