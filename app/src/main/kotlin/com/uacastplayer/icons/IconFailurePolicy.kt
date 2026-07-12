package com.uacastplayer.icons

data class FailureRecord(val recordedAtMillis: Long, val isPermanent: Boolean)

/**
 * A 404/403/etc. is very unlikely to start working again soon, so it's remembered on disk for a
 * long time; a network hiccup is remembered only in memory and only briefly, so a transient
 * outage doesn't permanently blacklist a perfectly good icon URL.
 */
object IconFailurePolicy {

    const val PERMANENT_TTL_MILLIS = 7L * 24 * 3_600_000L
    const val TRANSIENT_TTL_MILLIS = 3_600_000L

    fun isPermanentFailure(httpStatusCode: Int?, isNetworkError: Boolean): Boolean {
        if (isNetworkError || httpStatusCode == null) return false
        return httpStatusCode in PERMANENT_STATUS_CODES
    }

    fun isExpired(record: FailureRecord, nowMillis: Long): Boolean {
        val ttl = if (record.isPermanent) PERMANENT_TTL_MILLIS else TRANSIENT_TTL_MILLIS
        return nowMillis - record.recordedAtMillis > ttl
    }

    fun shouldSkip(record: FailureRecord?, nowMillis: Long): Boolean =
        record != null && !isExpired(record, nowMillis)

    private val PERMANENT_STATUS_CODES = setOf(400, 401, 403, 404, 410)
}
