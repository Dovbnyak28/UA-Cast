package com.uacastplayer.epg

object ProgrammeProgress {
    fun progress(startMillis: Long, effectiveStopMillis: Long, nowMillis: Long): Float {
        if (effectiveStopMillis <= startMillis) return 0f
        val fraction = (nowMillis - startMillis).toFloat() / (effectiveStopMillis - startMillis).toFloat()
        return fraction.coerceIn(0f, 1f)
    }
}
