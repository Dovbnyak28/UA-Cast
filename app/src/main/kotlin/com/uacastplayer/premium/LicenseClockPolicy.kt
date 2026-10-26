package com.uacastplayer.premium

/** Prevents clock rollback from reactivating expired legacy paid access. New Premium purchases
 * are non-expiring; this policy is retained only for records that already have an expiry. */
object LicenseClockPolicy {
    fun clampToHighWaterMark(nowMillis: Long, highWaterMarkMillis: Long): Long =
        maxOf(nowMillis, highWaterMarkMillis)
}
