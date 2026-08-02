package com.uacastplayer.data.icons

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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
            prefs.edit {
                for (key in prefs.all.keys) {
                    if (key != KEY_SCHEMA_VERSION) remove(key)
                }
                putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            }
        }
    }

    fun shouldSkip(url: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val key = Fingerprint.of(url)

        val permanentAt = if (prefs.contains(key)) prefs.getLong(key, 0L) else null
        if (permanentAt != null) {
            val record = FailureRecord(permanentAt, isPermanent = true)
            if (!IconFailurePolicy.isExpired(record, nowMillis)) return true
            prefs.edit { remove(key) }
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
            prefs.edit { putLong(key, nowMillis) }
        } else {
            transientFailures[key] = nowMillis
        }
    }

    /**
     * Drops every expired record, transient and permanent alike. Call once per playlist
     * load/refresh.
     *
     * Neither side shrinks on its own: [shouldSkip] only removes a record when that exact URL is
     * looked up again after expiring, and an icon URL stops being looked up the moment its channel
     * leaves the playlist - which is precisely when the user switches or edits a playlist source.
     *
     * For the transient map that just wastes a little heap. For the persisted side it is worse:
     * dead logo URLs number in the hundreds on a typical IPTV playlist, SharedPreferences parses
     * its whole file into memory on first access (blocking), and rewrites the whole file on every
     * apply(). Left alone the file only ever grows, so cold start gets slower over the months and
     * every new icon failure pays to rewrite a bigger file. The 7-day TTL was already there (see
     * [IconFailurePolicy.PERMANENT_TTL_MILLIS]) - nothing was enforcing it.
     */
    fun pruneExpiredFailures(nowMillis: Long = System.currentTimeMillis()) {
        transientFailures.entries.removeIf { (_, recordedAt) ->
            IconFailurePolicy.isExpired(FailureRecord(recordedAt, isPermanent = false), nowMillis)
        }
        prunePersistedFailures(nowMillis)
    }

    private fun prunePersistedFailures(nowMillis: Long) {
        val expired = prefs.all
            .filterKeys { it != KEY_SCHEMA_VERSION }
            // Anything that is not a Long is not a failure record this class wrote - skipped rather
            // than assumed, so a future key of another type here can never be deleted by accident.
            .filterValues { value ->
                value is Long && IconFailurePolicy.isExpired(FailureRecord(value, isPermanent = true), nowMillis)
            }
            .keys
        if (expired.isEmpty()) return
        prefs.edit { expired.forEach(::remove) }
    }

    private companion object {
        // Not a URL fingerprint (see Fingerprint.of) - namespaced so it can't collide with one.
        const val KEY_SCHEMA_VERSION = "__schema_version__"

        // Bump whenever a change means existing permanent records should be re-validated - e.g.
        // this value's introduction, for the browser User-Agent fix (see the init block above).
        const val SCHEMA_VERSION = 1
    }
}
