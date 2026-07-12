package com.uacastplayer.cast

data class IncompatibilityRecord(val recordedAtMillis: Long)

/** Remembers a (stream, receiver) pair that failed direct Cast delivery, for 30 days. */
object IncompatibilityMemoryPolicy {

    const val TTL_MILLIS = 30L * 24 * 3_600_000L

    fun isExpired(record: IncompatibilityRecord, nowMillis: Long): Boolean =
        nowMillis - record.recordedAtMillis > TTL_MILLIS

    fun shouldGoStraightToProxy(record: IncompatibilityRecord?, nowMillis: Long): Boolean =
        record != null && !isExpired(record, nowMillis)
}
