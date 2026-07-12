package com.uacastplayer.playlist

/**
 * Hand-rolled, dependency-free M3U/M3U8 parser. Deliberately tolerant of the many small dialect
 * differences seen in real-world IPTV playlists: a UTF-8 BOM, quoted or unquoted attribute
 * values, a display-name comma that must not be confused with commas inside quoted attributes,
 * and the legacy `#EXTGRP:` tag as a fallback for `group-title`.
 */
object M3uParser {

    private const val UTF8_BOM = "\uFEFF"
    private val attributePattern = Regex("""([a-zA-Z][\w-]*)=(?:"([^"]*)"|(\S+))""")

    fun parse(text: String): M3uParseResult {
        val normalized = text.removePrefix(UTF8_BOM)
        val lines = normalized.split("\n").map { it.trimEnd('\r') }

        val channels = mutableListOf<M3uChannel>()
        var skippedLineCount = 0
        var pendingExtinf: PendingExtinf? = null
        var pendingGroupOverride: String? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTM3U") -> Unit

                line.startsWith("#EXTINF:") -> {
                    if (pendingExtinf != null) skippedLineCount++
                    pendingExtinf = parseExtinf(line.substring("#EXTINF:".length))
                }

                line.startsWith("#EXTGRP:") -> {
                    pendingGroupOverride = line.substring("#EXTGRP:".length).trim().ifEmpty { null }
                }

                line.startsWith("#") -> Unit // unrecognized tag/comment, silently ignored

                else -> {
                    val extinf = pendingExtinf
                    if (extinf == null) {
                        skippedLineCount++
                    } else {
                        val displayName = extinf.displayName
                            ?: extinf.tvgName
                            ?: extinf.tvgId
                        if (displayName.isNullOrBlank()) {
                            skippedLineCount++
                        } else {
                            channels += M3uChannel(
                                displayName = displayName,
                                streamUrl = line,
                                tvgId = extinf.tvgId,
                                tvgName = extinf.tvgName,
                                tvgLogo = extinf.tvgLogo,
                                groupTitle = extinf.groupTitle ?: pendingGroupOverride,
                            )
                        }
                    }
                    pendingExtinf = null
                    pendingGroupOverride = null
                }
            }
        }

        if (pendingExtinf != null) skippedLineCount++

        return M3uParseResult(channels = channels, skippedLineCount = skippedLineCount)
    }

    private data class PendingExtinf(
        val displayName: String?,
        val tvgId: String?,
        val tvgName: String?,
        val tvgLogo: String?,
        val groupTitle: String?,
    )

    private fun parseExtinf(content: String): PendingExtinf {
        val splitIndex = indexOfUnquotedComma(content)
        val attributesSection: String
        val displayName: String?
        if (splitIndex == -1) {
            attributesSection = content
            displayName = null
        } else {
            attributesSection = content.substring(0, splitIndex)
            displayName = content.substring(splitIndex + 1).trim().ifEmpty { null }
        }

        var tvgId: String? = null
        var tvgName: String? = null
        var tvgLogo: String? = null
        var groupTitle: String? = null

        for (match in attributePattern.findAll(attributesSection)) {
            val key = match.groupValues[1].lowercase()
            val value = (match.groups[2]?.value ?: match.groups[3]?.value).orEmpty()
            when (key) {
                "tvg-id" -> tvgId = value.ifBlank { null }
                "tvg-name" -> tvgName = value.ifBlank { null }
                "tvg-logo" -> tvgLogo = value.ifBlank { null }
                "group-title" -> groupTitle = value.ifBlank { null }
            }
        }

        return PendingExtinf(displayName, tvgId, tvgName, tvgLogo, groupTitle)
    }

    private fun indexOfUnquotedComma(content: String): Int {
        var inQuotes = false
        for (i in content.indices) {
            when (content[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return i
            }
        }
        return -1
    }
}
