package com.uacastplayer.data.icons

import android.content.Context
import android.content.SharedPreferences
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.icons.FailureRecord
import com.uacastplayer.icons.IconFailurePolicy
import com.uacastplayer.icons.IconFailureStoreMigration
import java.util.concurrent.ConcurrentHashMap

/**
 * Permanent failures (404s etc.) persist across restarts; transient ones (network hiccups) live
 * only in memory for the current process, per [IconFailurePolicy].
 */
class IconFailureStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("uacast_icon_failures", Context.MODE_PRIVATE)
    private val transientFailures = ConcurrentHashMap<String, Long>()

    init {
        // Permanent records from before the icon fetch request carried a browser User-Agent (see
        // IconRepository.fetchAndValidate) may have blacklisted URLs that only 403'd because of
        // that missing header - clear them once per schema bump so those channels get a fresh
        // chance. See IconFailureStoreMigration for the stored-vs-current version decision.
        val storedVersion = if (prefs.contains(KEY_SCHEMA_VERSION)) prefs.getInt(KEY_SCHEMA_VERSION, 0) else null
        if (IconFailureStoreMigration.shouldClearPermanentFailures(storedVersion, SCHEMA_VERSION)) {
            val editor = prefs.edit()
            for (key in prefs.all.keys) {
                if (key != KEY_SCHEMA_VERSION) editor.remove(key)
            }
            editor.putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            editor.apply()
        }
    }

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

    /** [transientFailures] otherwise only shrinks when the same channel is looked up again after
     * its record expires (see [shouldSkip]) - a channel whose icon failed once and is never
     * looked up again (e.g. the user never scrolls back to it) keeps its entry for the rest of the
     * process's lifetime. Call once per playlist load/refresh. */
    fun pruneTransientFailures(nowMillis: Long = System.currentTimeMillis()) {
        transientFailures.entries.removeIf { (_, recordedAt) ->
            IconFailurePolicy.isExpired(FailureRecord(recordedAt, isPermanent = false), nowMillis)
        }
    }

    private companion object {
        // Not a URL fingerprint (see Fingerprint.of) - namespaced so it can't collide with one.
        const val KEY_SCHEMA_VERSION = "__schema_version__"

        // Bump whenever a change means existing permanent records should be re-validated - e.g.
        // this value's introduction, for the browser User-Agent fix (see the init block above).
        const val SCHEMA_VERSION = 1
    }
}
