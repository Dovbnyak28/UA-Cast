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

    /**
     * [lineSequence] rather than `split("\n").map { it.trimEnd('\r') }`: that built the whole
     * playlist a second time in memory - one String per line plus a backing array, then a second
     * array for the map - all of it alive at once, on top of the original text, for a file that is
     * routinely tens of megabytes on a large IPTV provider. The sequence hands over one line at a
     * time, so each becomes collectable as soon as the loop moves past it.
     *
     * It also splits on a lone `\r`, which the old `trimEnd` did not (it only stripped a trailing
     * one from a `\n`-delimited line). That is the more correct reading of a classic-Mac line
     * ending, and `\r` is a control character that cannot legitimately appear inside a channel name
     * or url anyway.
     */
    fun parse(text: String): M3uParseResult {
        val lines = text.removePrefix(UTF8_BOM).lineSequence()

        val channels = mutableListOf<M3uChannel>()
        var skippedLineCount = 0
        var pendingExtinf: PendingExtinf? = null
        var pendingGroupOverride: String? = null
        var pendingUserAgent: String? = null
        var pendingReferrer: String? = null
        // A real provider's group-title repeats hundreds of times over ("Movies", "Sports", "News"
        // on every one of thousands of channels), and each occurrence used to become its own fresh
        // String - one heap object per channel for a value that is, in practice, drawn from a
        // couple dozen distinct strings. Interning collapses that to one instance per distinct
        // title, the same fix XmlTvParser's channelIdPool already applies for the same reason on
        // its own hottest repeated field. Scoped to this call rather than held on the object: this
        // is a stateless singleton reused across every playlist parse, and a pool that outlived one
        // parse would just be a permanent retention of every group title ever seen.
        val groupTitlePool = HashMap<String, String>()
        // Only the (normally singular) #EXTM3U line ever carries these, so first-found wins - no
        // need to keep scanning once set.
        var epgUrls: List<String> = emptyList()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTM3U") -> {
                    if (epgUrls.isEmpty()) epgUrls = parseEpgUrls(line.substring("#EXTM3U".length))
                }

                line.startsWith("#EXTINF:") -> {
                    if (pendingExtinf != null) skippedLineCount++
                    pendingExtinf = parseExtinf(line.substring("#EXTINF:".length))
                }

                line.startsWith("#EXTGRP:") -> {
                    pendingGroupOverride = line.substring("#EXTGRP:".length).trim().ifEmpty { null }
                }

                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val option = parseExtVlcOpt(line.substring("#EXTVLCOPT:".length))
                    when (option?.first?.lowercase()) {
                        "http-user-agent" -> pendingUserAgent = option.second.ifEmpty { null }
                        "http-referrer" -> pendingReferrer = option.second.ifEmpty { null }
                    }
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
                                groupTitle = (extinf.groupTitle ?: pendingGroupOverride)
                                    ?.let { groupTitlePool.getOrPut(it) { it } },
                                userAgent = pendingUserAgent,
                                referrer = pendingReferrer,
                            )
                        }
                    }
                    pendingExtinf = null
                    pendingGroupOverride = null
                    pendingUserAgent = null
                    pendingReferrer = null
                }
            }
        }

        if (pendingExtinf != null) skippedLineCount++

        return M3uParseResult(channels = channels, skippedLineCount = skippedLineCount, epgUrls = epgUrls)
    }

    /** `url-tvg`/`x-tvg-url` (case-insensitive, providers use either) on the `#EXTM3U` line - the
     * de-facto convention for pointing a player at the provider's own EPG. The value can be a
     * comma-separated list of several URLs; [attributePattern] already handles both quoted and
     * bare attribute values. */
    private fun parseEpgUrls(content: String): List<String> {
        val urls = mutableListOf<String>()
        for (match in attributePattern.findAll(content)) {
            val key = match.groupValues[1].lowercase()
            if (key != "url-tvg" && key != "x-tvg-url") continue
            val value = (match.groups[2]?.value ?: match.groups[3]?.value).orEmpty()
            urls += value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }
        return urls
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

    /** `#EXTVLCOPT:key=value` - value runs to the end of the line (no quoting convention here, unlike EXTINF attributes). */
    private fun parseExtVlcOpt(content: String): Pair<String, String>? {
        val separatorIndex = content.indexOf('=')
        if (separatorIndex == -1) return null
        val key = content.substring(0, separatorIndex).trim()
        val value = content.substring(separatorIndex + 1).trim()
        return key to value
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
