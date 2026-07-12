package com.uacastplayer.data.cast

import android.content.Context
import com.uacastplayer.cast.IncompatibilityRecord
import com.uacastplayer.core.security.Fingerprint

private const val MIN_WRITE_INTERVAL_MILLIS = 2_000L

/** Disk-backed (stream-fingerprint, receiver-fingerprint) -> incompatibility timestamp map. */
class IncompatibilityMemoryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("uacast_cast_incompatibility", Context.MODE_PRIVATE)
    private var lastWriteAtMillis = 0L

    fun lookup(streamUrl: String, receiverId: String): IncompatibilityRecord? {
        val key = keyFor(streamUrl, receiverId)
        return if (prefs.contains(key)) IncompatibilityRecord(prefs.getLong(key, 0L)) else null
    }

    fun record(streamUrl: String, receiverId: String, nowMillis: Long = System.currentTimeMillis()) {
        if (nowMillis - lastWriteAtMillis < MIN_WRITE_INTERVAL_MILLIS) return
        lastWriteAtMillis = nowMillis
        prefs.edit().putLong(keyFor(streamUrl, receiverId), nowMillis).apply()
    }

    private fun keyFor(streamUrl: String, receiverId: String): String = Fingerprint.of("$streamUrl|$receiverId")
}
