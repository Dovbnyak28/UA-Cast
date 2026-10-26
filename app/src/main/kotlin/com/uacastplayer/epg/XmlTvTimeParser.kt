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

    /** Epoch millis for the `yyyyMMddHHmmss` block at [from], before any UTC offset is applied. */
    private fun readCivilMillis(value: String, from: Int): Long? {
        val year = readDigits(value, from + YEAR_OFFSET, YEAR_WIDTH)
        val month = readDigits(value, from + MONTH_OFFSET, FIELD_WIDTH)
        val day = readDigits(value, from + DAY_OFFSET, FIELD_WIDTH)
        val hour = readDigits(value, from + HOUR_OFFSET, FIELD_WIDTH)
        val minute = readDigits(value, from + MINUTE_OFFSET, FIELD_WIDTH)
        val second = readDigits(value, from + SECOND_OFFSET, FIELD_WIDTH)
        return if (isDateInRange(year, month, day) && isTimeInRange(hour, minute, second)) {
            val dayMillis = daysFromCivil(year, month, day) * MILLIS_PER_DAY
            dayMillis + hour * MILLIS_PER_HOUR + minute * MILLIS_PER_MINUTE + second * MILLIS_PER_SECOND
        } else {
            null
        }
    }

    private fun isDateInRange(year: Int, month: Int, day: Int): Boolean =
        // Day is only bounded at 31 because a short month's overflow is carried, not rejected -
        // see daysFromCivil.
        year >= 0 && month in 1..MONTHS_PER_YEAR && day in 1..MAX_DAY_OF_MONTH

    // Second allows 60 for a leap second.
    private fun isTimeInRange(hour: Int, minute: Int, second: Int): Boolean =
        hour in 0..MAX_HOUR && minute in 0..MAX_MINUTE && second in 0..MAX_SECOND

    /** Zero when no offset follows the timestamp; null when what follows is not a valid one. */
    private fun readOffsetMillis(value: String, from: Int, to: Int): Long? {
        var start = from
        while (start < to && value[start].isWhitespace()) start++
        return if (start == to) 0L else parseOffsetMillis(value, start, to)
    }

    private fun parseOffsetMillis(value: String, from: Int, to: Int): Long? {
        val sign = when (value[from]) {
            '+' -> 1
            '-' -> -1
            else -> INVALID_SIGN
        }
        val hasExactWidth = to - from - SIGN_WIDTH == OFFSET_DIGITS
        val hours = if (hasExactWidth) readDigits(value, from + SIGN_WIDTH, FIELD_WIDTH) else INVALID_FIELD
        val minutes = if (hasExactWidth) {
            readDigits(value, from + SIGN_WIDTH + FIELD_WIDTH, FIELD_WIDTH)
        } else {
            INVALID_FIELD
        }
        val valid = sign != INVALID_SIGN && hours in 0..MAX_HOUR && minutes in 0..MAX_MINUTE
        return if (valid) sign * (hours * MILLIS_PER_HOUR + minutes * MILLIS_PER_MINUTE) else null
    }

    /**
     * Reads exactly [count] ASCII digits at [from], or [INVALID_FIELD]. A primitive sentinel keeps
     * this million-call parser path free of nullable-Int boxing.
     */
    private fun readDigits(value: String, from: Int, count: Int): Int {
        var acc = 0
        var valid = true
        for (i in from until from + count) {
            val digit = value[i] - '0'
            if (digit !in 0..MAX_DIGIT) valid = false
            if (valid) acc = acc * DECIMAL_RADIX + digit
        }
        return if (valid) acc else INVALID_FIELD
    }

    /**
     * Days from 1970-01-01 for a proleptic Gregorian date (Howard Hinnant's `days_from_civil`,
     * the standard shift-the-year-to-start-in-March formulation). Out-of-range days carry into
     * the following month exactly as the lenient [java.util.Calendar] this replaced did, so a
     * feed's "20260230" still resolves to 2 March rather than being lost.
     *
     * The named coefficients below are the fixed constants from that published algorithm.
     */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val shiftedYear = if (month <= FEBRUARY) year - 1 else year
        val eraYear = if (shiftedYear >= 0) shiftedYear else shiftedYear - (YEARS_PER_ERA - 1)
        val era = eraYear / YEARS_PER_ERA
        val yearOfEra = shiftedYear - era * YEARS_PER_ERA
        val shiftedMonth = (month + MARCH_BASED_MONTH_SHIFT) % MONTHS_PER_YEAR
        val dayOfYear = (DAYS_FORMULA_MULTIPLIER * shiftedMonth + DAYS_FORMULA_OFFSET) /
            DAYS_FORMULA_DIVISOR + day - 1
        val dayOfEra = yearOfEra * DAYS_PER_COMMON_YEAR + yearOfEra / LEAP_YEAR_DIVISOR -
            yearOfEra / NON_LEAP_CENTURY_DIVISOR + dayOfYear
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
    private const val INVALID_FIELD = -1
    private const val INVALID_SIGN = 0

    private const val MAX_DAY_OF_MONTH = 31
    private const val MAX_HOUR = 23
    private const val MAX_MINUTE = 59
    private const val MAX_SECOND = 60
    private const val MONTHS_PER_YEAR = 12

    private const val FEBRUARY = 2
    private const val YEARS_PER_ERA = 400
    private const val MARCH_BASED_MONTH_SHIFT = 9
    private const val DAYS_FORMULA_MULTIPLIER = 153
    private const val DAYS_FORMULA_OFFSET = 2
    private const val DAYS_FORMULA_DIVISOR = 5
    private const val DAYS_PER_COMMON_YEAR = 365
    private const val LEAP_YEAR_DIVISOR = 4
    private const val NON_LEAP_CENTURY_DIVISOR = 100

    /** `HHMM` - the digit count a `±ZZZZ` UTC offset must carry after its sign. */
    private const val OFFSET_DIGITS = 4
    private const val SIGN_WIDTH = 1

    /** `yyyyMMddHHmmss` - the fixed-width block every XMLTV timestamp starts with. */
    private const val TIMESTAMP_WIDTH = 14
    private const val YEAR_WIDTH = 4
    private const val YEAR_OFFSET = 0
    private const val MONTH_OFFSET = 4
    private const val DAY_OFFSET = 6
    private const val HOUR_OFFSET = 8
    private const val MINUTE_OFFSET = 10
    private const val SECOND_OFFSET = 12
}
