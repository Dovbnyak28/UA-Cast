package com.uacastplayer.proxy

/** Inclusive byte interval. Length arithmetic is bounded by the actual source length. */
data class LocalVideoRange(val start: Long, val end: Long) {
    val length: Long get() = end - start + 1

    companion object {
        /** null means malformed/unsatisfiable; multiple ranges are deliberately not supported. */
        fun parse(header: String?, length: Long): LocalVideoRange? = when {
            length <= 0 -> null
            header == null -> LocalVideoRange(0, length - 1)
            else -> RANGE_PATTERN.matchEntire(header.trim())?.let {
                fromParts(it.groupValues[1], it.groupValues[2], length)
            }
        }

        private fun fromParts(first: String, last: String, length: Long): LocalVideoRange? = if (first.isEmpty()) {
            last.toLongOrNull()?.takeIf { it > 0 }?.let { suffix ->
                LocalVideoRange((length - suffix).coerceAtLeast(0), length - 1)
            }
        } else {
            first.toLongOrNull()?.takeIf { it in 0 until length }?.let { start ->
                val end = if (last.isEmpty()) length - 1 else last.toLongOrNull()
                end?.takeIf { it >= start }?.let { LocalVideoRange(start, minOf(it, length - 1)) }
            }
        }

        private val RANGE_PATTERN = Regex("bytes=([0-9]*)-([0-9]*)", RegexOption.IGNORE_CASE)
    }
}
