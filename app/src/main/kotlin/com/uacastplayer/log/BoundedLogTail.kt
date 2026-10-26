package com.uacastplayer.log

import java.util.ArrayDeque

/**
 * Keeps only the newest complete log lines within both a line and character budget.
 *
 * Unlike `lines.toList().takeLast(...)`, memory stays bounded while the source is still being
 * consumed. An individual oversized line is clipped from the front so its most recent tail is
 * retained; when several lines exceed the character budget, whole oldest lines are discarded so
 * a diagnostics attachment never starts in the middle of an ordinary line.
 */
internal class BoundedLogTail(
    private val maxLines: Int,
    private val maxChars: Int,
) {
    private val lines = ArrayDeque<String>()
    private var charCount = 0

    init {
        require(maxLines > 0) { "maxLines must be positive" }
        require(maxChars > 0) { "maxChars must be positive" }
    }

    fun add(line: String) {
        val boundedLine = line.takeLast(maxChars)
        if (lines.isNotEmpty()) charCount += LINE_SEPARATOR_LENGTH
        lines.addLast(boundedLine)
        charCount += boundedLine.length

        while (lines.size > maxLines || (charCount > maxChars && lines.size > 1)) {
            removeOldest()
        }
    }

    fun contentOrNull(): String? = lines
        .joinToString("\n")
        .ifBlank { null }

    internal fun retainedCharCount(): Int = charCount

    private fun removeOldest() {
        charCount -= lines.removeFirst().length
        if (lines.isNotEmpty()) charCount -= LINE_SEPARATOR_LENGTH
    }

    private companion object {
        const val LINE_SEPARATOR_LENGTH = 1
    }
}
