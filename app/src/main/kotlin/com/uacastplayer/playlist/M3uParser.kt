package com.uacastplayer.playlist

/**
 * Hand-rolled, dependency-free M3U/M3U8 parser. Deliberately tolerant of the many small dialect
 * differences seen in real-world IPTV playlists: a UTF-8 BOM, quoted or unquoted attribute
 * values, a display-name comma that must not be confused with commas inside quoted attributes,
 * and the legacy `#EXTGRP:` tag as a fallback for `group-title`.
 */
object M3uParser {

    private const val UTF8_BOM = "\uFEFF"
    private const val CANCELLATION_CHECK_INTERVAL_LINES = 256
    private const val QUOTED_ATTRIBUTE_VALUE_GROUP = 2
    private const val UNQUOTED_ATTRIBUTE_VALUE_GROUP = 3
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
    fun parse(text: String, checkCancellation: () -> Unit = {}): M3uParseResult {
        val state = ParseState()
        var linesUntilCancellationCheck = CANCELLATION_CHECK_INTERVAL_LINES
        checkCancellation()
        for (rawLine in text.removePrefix(UTF8_BOM).lineSequence()) {
            linesUntilCancellationCheck--
            if (linesUntilCancellationCheck == 0) {
                checkCancellation()
                linesUntilCancellationCheck = CANCELLATION_CHECK_INTERVAL_LINES
            }
            val line = rawLine.trim()
            if (line.isNotEmpty()) state.accept(line)
        }
        checkCancellation()
        return state.finish()
    }

    private class ParseState {
        private val channels = mutableListOf<M3uChannel>()
        private val groupTitlePool = HashMap<String, String>()
        private var skippedLineCount = 0
        private var pendingExtinf: PendingExtinf? = null
        private var pendingGroupOverride: String? = null
        private var pendingUserAgent: String? = null
        private var pendingReferrer: String? = null
        private var epgUrls: List<String> = emptyList()

        fun accept(line: String) {
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> acceptHeader(line)
                line.startsWith("#EXTINF:", ignoreCase = true) -> acceptExtinf(line)
                line.startsWith("#EXTGRP:", ignoreCase = true) -> acceptGroup(line)
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> acceptVlcOption(line)
                line.startsWith("#") -> Unit
                else -> acceptStreamUrl(line)
            }
        }

        fun finish(): M3uParseResult {
            if (pendingExtinf != null) skippedLineCount++
            return M3uParseResult(channels, skippedLineCount, epgUrls)
        }

        private fun acceptHeader(line: String) {
            if (epgUrls.isEmpty()) epgUrls = parseEpgUrls(line.substring("#EXTM3U".length))
        }

        private fun acceptExtinf(line: String) {
            if (pendingExtinf != null) skippedLineCount++
            pendingExtinf = parseExtinf(line.substring("#EXTINF:".length))
        }

        private fun acceptGroup(line: String) {
            pendingGroupOverride = line.substring("#EXTGRP:".length).trim().ifEmpty { null }
        }

        private fun acceptVlcOption(line: String) {
            val option = parseExtVlcOpt(line.substring("#EXTVLCOPT:".length))
            when (option?.first?.lowercase()) {
                "http-user-agent" -> pendingUserAgent = option.second.ifEmpty { null }
                "http-referrer" -> pendingReferrer = option.second.ifEmpty { null }
            }
        }

        private fun acceptStreamUrl(streamUrl: String) {
            val extinf = pendingExtinf
            val displayName = extinf?.displayName ?: extinf?.tvgName ?: extinf?.tvgId
            if (extinf == null || displayName.isNullOrBlank()) {
                skippedLineCount++
            } else {
                channels += extinf.toChannel(streamUrl, displayName)
            }
            clearPendingChannel()
        }

        private fun PendingExtinf.toChannel(streamUrl: String, displayName: String): M3uChannel =
            M3uChannel(
                displayName = displayName,
                streamUrl = streamUrl,
                tvgId = tvgId,
                tvgName = tvgName,
                tvgLogo = tvgLogo,
                groupTitle = (groupTitle ?: pendingGroupOverride)
                    ?.let { groupTitlePool.getOrPut(it) { it } },
                userAgent = pendingUserAgent,
                referrer = pendingReferrer,
            )

        private fun clearPendingChannel() {
            pendingExtinf = null
            pendingGroupOverride = null
            pendingUserAgent = null
            pendingReferrer = null
        }
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
            val value = (
                match.groups[QUOTED_ATTRIBUTE_VALUE_GROUP]?.value
                    ?: match.groups[UNQUOTED_ATTRIBUTE_VALUE_GROUP]?.value
                ).orEmpty()
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
            val value = (
                match.groups[QUOTED_ATTRIBUTE_VALUE_GROUP]?.value
                    ?: match.groups[UNQUOTED_ATTRIBUTE_VALUE_GROUP]?.value
                ).orEmpty()
            when (key) {
                "tvg-id" -> tvgId = normalizeAttributeValue(value)
                "tvg-name" -> tvgName = normalizeAttributeValue(value)
                "tvg-logo" -> tvgLogo = normalizeAttributeValue(value)
                "group-title" -> groupTitle = normalizeAttributeValue(value)
            }
        }

        return PendingExtinf(displayName, tvgId, tvgName, tvgLogo, groupTitle)
    }

    private fun normalizeAttributeValue(value: String): String? = value.trim().ifEmpty { null }

    /** `#EXTVLCOPT:key=value`; unlike EXTINF attributes, its value runs to the line end. */
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
