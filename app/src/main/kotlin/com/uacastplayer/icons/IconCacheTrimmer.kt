package com.uacastplayer.icons

data class CacheEntry(val key: String, val sizeBytes: Long, val lastAccessedMillis: Long)

/** Decides which cached icons to evict, oldest-accessed first, until both limits are satisfied. */
object IconCacheTrimmer {

    const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
    const val MAX_COUNT = 20_000

    fun selectEntriesToEvict(
        entries: List<CacheEntry>,
        maxTotalBytes: Long = MAX_TOTAL_BYTES,
        maxCount: Int = MAX_COUNT,
    ): List<CacheEntry> {
        val sortedOldestFirst = entries.sortedBy { it.lastAccessedMillis }
        var totalBytes = entries.sumOf { it.sizeBytes }
        var count = entries.size
        val toEvict = mutableListOf<CacheEntry>()

        for (entry in sortedOldestFirst) {
            if (totalBytes <= maxTotalBytes && count <= maxCount) break
            toEvict += entry
            totalBytes -= entry.sizeBytes
            count -= 1
        }
        return toEvict
    }
}
