package com.uacastplayer.data.icons

import android.content.Context
import android.content.SharedPreferences
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.icons.FailureRecord
import com.uacastplayer.icons.IconFailurePolicy
import java.util.concurrent.ConcurrentHashMap

/**
 * Permanent failures (404s etc.) persist across restarts; transient ones (network hiccups) live
 * only in memory for the current process, per [IconFailurePolicy].
 */
class IconFailureStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("uacast_icon_failures", Context.MODE_PRIVATE)
    private val transientFailures = ConcurrentHashMap<String, Long>()

    fun shouldSkip(url: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val key = Fingerprint.of(url)

        val permanentAt = if (prefs.contains(key)) prefs.getLong(key, 0L) else null
        if (permanentAt != null) {
            val record = FailureRecord(permanentAt, isPermanent = true)
            if (!IconFailurePolicy.isExpired(record, nowMillis)) return true
            prefs.edit().remove(key).apply()
        }

        val transientAt = transientFailures[key]
        if (transientAt != null) {
            val record = FailureRecord(transientAt, isPermanent = false)
            if (!IconFailurePolicy.isExpired(record, nowMillis)) return true
            transientFailures.remove(key)
        }

        return false
    }

    fun recordFailure(url: String, isPermanent: Boolean, nowMillis: Long = System.currentTimeMillis()) {
        val key = Fingerprint.of(url)
        if (isPermanent) {
            prefs.edit().putLong(key, nowMillis).apply()
        } else {
            transientFailures[key] = nowMillis
        }
    }
}
