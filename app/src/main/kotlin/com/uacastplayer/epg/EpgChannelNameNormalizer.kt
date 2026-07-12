package com.uacastplayer.epg

import java.text.Normalizer

/**
 * Normalizes a channel name for matching an M3U channel against an XMLTV `<channel>` when tvg-id
 * lookup fails: NFKC normalization, quality-suffix stripping (HD/FHD/4K and similar), and a small
 * alias dictionary for common regional spelling differences.
 */
object EpgChannelNameNormalizer {

    private val qualitySuffixPattern = Regex(
        """\s*[\[(]?\b(4K|UHD|FHD|HD|SD)\b[\])]?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val aliases: Map<String, String> = mapOf(
        "1+1" to "plus one",
        "перший" to "first",
        "первый" to "first",
    )

    fun normalize(name: String): String {
        var value = Normalizer.normalize(name, Normalizer.Form.NFKC).trim()
        value = qualitySuffixPattern.replace(value, "")
        value = value.trim().lowercase().replace('ё', 'е')
        return aliases[value] ?: value
    }
}
