package com.uacastplayer.epg

/**
 * Parses XMLTV's `yyyyMMddHHmmss ±ZZZZ` timestamp format into epoch milliseconds (UTC).
 *
 * The conversion is plain arithmetic rather than a [java.util.Calendar]. This runs twice per
 * `<programme>` - up to 500k times for one feed - and the Calendar version allocated a
 * `GregorianCalendar` on every call and looked the UTC zone up through
 * `TimeZone.getTimeZone`, which is internally synchronized. A field capture showed the EPG
 * worker holding that monitor for 1.2s at a stretch while unrelated threads queued behind it.
 */
object XmlTvTimeParser {

    // Indices into `value` rather than substrings: the six date fields alone were six throwaway
    // Strings per timestamp, which is three million of them across one large feed.
    fun parse(value: String): Long? {
        var start = 0
        var end = value.length
        while (start < end && value[start].isWhitespace()) start++
        while (end > start && value[end - 1].isWhitespace()) end--
        if (end - start < TIMESTAMP_WIDTH) return null

        val civilMillis = readCivilMillis(value, start)
        val offsetMillis = readOffsetMillis(value, start + TIMESTAMP_WIDTH, end)
        return if (civilMillis == null || offsetMillis == null) null else civilMillis - offsetMillis
    }

    /**
     * Epoch millis for the `yyyyMMddHHmmss` block at [from], before any UTC offset is applied.
     * The literal offsets are the field positions in that fixed-width layout, which the format
     * string above names better than a constant per field would.
     */
    @Suppress("ReturnCount", "MagicNumber")
    private fun readCivilMillis(value: String, from: Int): Long? {
        val year = readDigits(value, from, YEAR_WIDTH) ?: return null
        val month = readDigits(value, from + 4, FIELD_WIDTH) ?: return null
        val day = readDigits(value, from + 6, FIELD_WIDTH) ?: return null
        val hour = readDigits(value, from + 8, FIELD_WIDTH) ?: return null
        val minute = readDigits(value, from + 10, FIELD_WIDTH) ?: return null
        val second = readDigits(value, from + 12, FIELD_WIDTH) ?: return null
        if (!isInRange(month, day, hour, minute, second)) return null

        val dayMillis = daysFromCivil(year, month, day) * MILLIS_PER_DAY
        return dayMillis + hour * MILLIS_PER_HOUR + minute * MILLIS_PER_MINUTE + second * MILLIS_PER_SECOND
    }

    @Suppress("ComplexCondition", "MagicNumber")
    private fun isInRange(month: Int, day: Int, hour: Int, minute: Int, second: Int): Boolean =
        // Second allows 60 for a leap second; day is only bounded at 31 because a short month's
        // overflow is carried, not rejected - see daysFromCivil.
        month in 1..12 && day in 1..31 && hour in 0..23 && minute in 0..59 && second in 0..60

    /** Zero when no offset follows the timestamp; null when what follows is not a valid one. */
    private fun readOffsetMillis(value: String, from: Int, to: Int): Long? {
        var start = from
        while (start < to && value[start].isWhitespace()) start++
        return if (start == to) 0L else parseOffsetMillis(value, start, to)
    }

    @Suppress("ReturnCount")
    private fun parseOffsetMillis(value: String, from: Int, to: Int): Long? {
        val sign = when (value[from]) {
            '+' -> 1
            '-' -> -1
            else -> return null
        }
        // Every character after the sign must be a digit, as before - "+0200x" is not an offset.
        if (to - from - 1 < OFFSET_DIGITS) return null
        for (i in from + 1 until to) {
            if (value[i] !in '0'..'9') return null
        }
        val hours = readDigits(value, from + 1, FIELD_WIDTH) ?: return null
        val minutes = readDigits(value, from + 1 + FIELD_WIDTH, FIELD_WIDTH) ?: return null
        return sign * (hours * MILLIS_PER_HOUR + minutes * MILLIS_PER_MINUTE)
    }

    /** Reads exactly [count] ASCII digits at [from]; null if any of them is not a digit. */
    private fun readDigits(value: String, from: Int, count: Int): Int? {
        var acc = 0
        for (i in from until from + count) {
            val digit = value[i] - '0'
            if (digit !in 0..MAX_DIGIT) return null
            acc = acc * DECIMAL_RADIX + digit
        }
        return acc
    }

    /**
     * Days from 1970-01-01 for a proleptic Gregorian date (Howard Hinnant's `days_from_civil`,
     * the standard shift-the-year-to-start-in-March formulation). Out-of-range days carry into
     * the following month exactly as the lenient [java.util.Calendar] this replaced did, so a
     * feed's "20260230" still resolves to 2 March rather than being lost.
     *
     * MagicNumber is suppressed rather than satisfied: every literal below is a fixed coefficient
     * of that published algorithm, and naming `153` or `5` individually would describe the
     * arithmetic step without explaining anything the reference doesn't.
     */
    @Suppress("MagicNumber")
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val shiftedYear = if (month <= 2) year - 1 else year
        val era = (if (shiftedYear >= 0) shiftedYear else shiftedYear - 399) / 400
        val yearOfEra = shiftedYear - era * 400
        val shiftedMonth = (month + 9) % 12
        val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era.toLong() * DAYS_PER_ERA + dayOfEra.toLong() - DAYS_FROM_ERA_START_TO_EPOCH
    }

    private const val MILLIS_PER_DAY = 86_400_000L
    private const val MILLIS_PER_HOUR = 3_600_000L
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_SECOND = 1_000L

    /** Every field in `yyyyMMddHHmmss` past the year, and in a `±HHMM` offset, is two digits. */
    private const val FIELD_WIDTH = 2

    /** Days in one 400-year Gregorian era, and the offset from era start (0000-03-01) to epoch. */
    private const val DAYS_PER_ERA = 146_097L
    private const val DAYS_FROM_ERA_START_TO_EPOCH = 719_468L

    private const val DECIMAL_RADIX = 10
    private const val MAX_DIGIT = 9

    /** `HHMM` - the digit count a `±ZZZZ` UTC offset must carry after its sign. */
    private const val OFFSET_DIGITS = 4

    /** `yyyyMMddHHmmss` - the fixed-width block every XMLTV timestamp starts with. */
    private const val TIMESTAMP_WIDTH = 14
    private const val YEAR_WIDTH = 4
}
