package com.uacastplayer.cast

data class IncompatibilityRecord(val recordedAtMillis: Long)

/** Remembers a (stream, receiver) pair that failed direct Cast delivery, for 30 days. */
object IncompatibilityMemoryPolicy {

    const val TTL_MILLIS = 30L * 24 * 3_600_000L

    /** Hard cap independent of [TTL_MILLIS] - a heavy user can accumulate this many distinct
     * failing (stream, receiver) pairs within 30 days on a large playlist; TTL alone wouldn't
     * bound the file's size in that case. */
    const val MAX_ENTRIES = 500

    fun isExpired(record: IncompatibilityRecord, nowMillis: Long): Boolean =
        nowMillis - record.recordedAtMillis > TTL_MILLIS

    fun shouldGoStraightToProxy(record: IncompatibilityRecord?, nowMillis: Long): Boolean =
        record != null && !isExpired(record, nowMillis)

    /** Which keys of [entries] (key -> recordedAtMillis) [IncompatibilityMemoryStore] should drop:
     * every expired one, plus - if still over [maxEntries] after that - the oldest survivors. */
    fun keysToPrune(entries: Map<String, Long>, nowMillis: Long, maxEntries: Int = MAX_ENTRIES): Set<String> {
        val expired = entries.filterValues { isExpired(IncompatibilityRecord(it), nowMillis) }.keys
        val remaining = entries - expired
        val overflow = if (remaining.size > maxEntries) {
            remaining.entries.sortedBy { it.value }.take(remaining.size - maxEntries).map { it.key }
        } else {
            emptyList()
        }
        return expired + overflow
    }
}
