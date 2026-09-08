package com.uacastplayer.epg

/** Owns document-wide alias budgets as well as the current channel's ordered, distinct names. */
internal class XmlTvChannelNames {
    private var names = linkedSetOf<String>()
    private var totalNames = 0
    private var totalChars = 0
    var limited = false
        private set

    fun beginChannel() {
        names = linkedSetOf()
    }

    fun add(name: String) {
        if (name in names) return
        if (names.size >= MAX_PER_CHANNEL || totalNames >= MAX_TOTAL_NAMES ||
            name.length > MAX_TOTAL_CHARS - totalChars
        ) {
            limited = true
            return
        }
        names.add(name)
        totalNames++
        totalChars += name.length
    }

    fun finishChannel(): List<String> = names.toList()

    companion object {
        const val MAX_PER_CHANNEL = 16
        const val MAX_TOTAL_NAMES = 100_000
        const val MAX_TOTAL_CHARS = 2 * 1024 * 1024
    }
}
