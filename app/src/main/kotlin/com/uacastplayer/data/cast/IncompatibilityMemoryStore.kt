package com.uacastplayer.data.cast

import android.content.Context
import androidx.core.content.edit
import com.uacastplayer.cast.IncompatibilityMemoryPolicy
import com.uacastplayer.cast.IncompatibilityRecord
import com.uacastplayer.core.security.Fingerprint

private const val MIN_WRITE_INTERVAL_MILLIS = 2_000L

// Bumped for the block-1.6 recording-rule change: records written under the old rule could be a
// transient mid-playback blip that happened to lose the recovery race, not a genuine
// incompatibility - see the one-time wipe in migrateIfNeeded().
private const val SCHEMA_VERSION = 1
private const val SCHEMA_VERSION_KEY = "schema_version"

/**
 * Disk-backed (stream-fingerprint, receiver-fingerprint) -> timestamp map.
 *
 * Read for exactly one question - "should this pair skip the direct attempt and go straight to the
 * proxy?" (`CastDeliveryStrategy.initialMode`) - so despite the name, a record does not assert
 * anything as specific as a codec problem. Two rules write here, and both mean the same thing to
 * the reader: a confirmed hard-incompatible codec (`IncompatibilityRecordingPolicy`) and a direct
 * attempt that never played where the proxy then did (`DirectRouteMemoryPolicy`). Nothing else
 * reads this store, so the distinction never has to be recovered from it.
 */
class IncompatibilityMemoryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("uacast_cast_incompatibility", Context.MODE_PRIVATE)

    // Per key, not one global timestamp: the interval exists to absorb repeated records of the
    // SAME failure (a burst of receiver errors for one channel), and a global stamp would also
    // silently drop a legitimate first record for a different (stream, receiver) pair that
    // happened to arrive within the window. Bounded by the number of distinct failing pairs seen
    // this process lifetime - tiny in practice.
    private val lastWriteAtMillisByKey = mutableMapOf<String, Long>()

    init {
        migrateIfNeeded()
        pruneExpired()
    }

    /** A one-time wipe when [SCHEMA_VERSION] advances - every entry here is keyed by an opaque
     * fingerprint anyway (see [keyFor]), so there is nothing worth trying to preserve across a rule
     * change; simply clearing and re-stamping the version is both correct and far simpler than
     * trying to re-derive which old entries would still qualify under the new rule. */
    private fun migrateIfNeeded() {
        if (prefs.getInt(SCHEMA_VERSION_KEY, 0) >= SCHEMA_VERSION) return
        prefs.edit { clear().putInt(SCHEMA_VERSION_KEY, SCHEMA_VERSION) }
    }

    fun lookup(streamUrl: String, receiverId: String): IncompatibilityRecord? {
        val key = keyFor(streamUrl, receiverId)
        return if (prefs.contains(key)) IncompatibilityRecord(prefs.getLong(key, 0L)) else null
    }

    fun record(streamUrl: String, receiverId: String, nowMillis: Long = System.currentTimeMillis()) {
        val key = keyFor(streamUrl, receiverId)
        val lastWriteAtMillis = lastWriteAtMillisByKey[key] ?: 0L
        if (nowMillis - lastWriteAtMillis < MIN_WRITE_INTERVAL_MILLIS) return
        lastWriteAtMillisByKey[key] = nowMillis
        prefs.edit { putLong(key, nowMillis) }
    }

    private fun keyFor(streamUrl: String, receiverId: String): String = Fingerprint.of("$streamUrl|$receiverId")

    /** The TTL in [lookup] only hides an expired record, it never deletes it - without this, the
     * file grows forever for a heavy user (thousands of channels over a year). Runs once per
     * process start, which is enough: this store sees at most a few writes per cast attempt. */
    private fun pruneExpired(nowMillis: Long = System.currentTimeMillis()) {
        val entries = prefs.all.filterKeys { it != SCHEMA_VERSION_KEY }
            .mapNotNull { (key, value) -> (value as? Long)?.let { key to it } }
            .toMap()
        val toRemove = IncompatibilityMemoryPolicy.keysToPrune(entries, nowMillis)
        if (toRemove.isEmpty()) return
        prefs.edit { for (key in toRemove) remove(key) }
    }
}
