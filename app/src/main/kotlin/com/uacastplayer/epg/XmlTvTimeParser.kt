package com.uacastplayer.epg

import java.util.Calendar
import java.util.TimeZone

/** Parses XMLTV's `yyyyMMddHHmmss ±ZZZZ` timestamp format into epoch milliseconds (UTC). */
object XmlTvTimeParser {

    fun parse(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.length < 14) return null
        val datePart = trimmed.substring(0, 14)

        val year = datePart.substring(0, 4).toIntOrNull() ?: return null
        val month = datePart.substring(4, 6).toIntOrNull() ?: return null
        val day = datePart.substring(6, 8).toIntOrNull() ?: return null
        val hour = datePart.substring(8, 10).toIntOrNull() ?: return null
        val minute = datePart.substring(10, 12).toIntOrNull() ?: return null
        val second = datePart.substring(12, 14).toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..60) {
            return null
        }

        val offsetPart = trimmed.substring(14).trim()
        val offsetMillis = if (offsetPart.isEmpty()) 0L else parseOffsetMillis(offsetPart) ?: return null

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute, second)
        return calendar.timeInMillis - offsetMillis
    }

    private fun parseOffsetMillis(offset: String): Long? {
        val sign = when (offset[0]) {
            '+' -> 1
            '-' -> -1
            else -> return null
        }
        val digits = offset.substring(1)
        if (digits.length < 4 || !digits.all(Char::isDigit)) return null
        val hours = digits.substring(0, 2).toInt()
        val minutes = digits.substring(2, 4).toInt()
        return sign * (hours * 3_600_000L + minutes * 60_000L)
    }
}
