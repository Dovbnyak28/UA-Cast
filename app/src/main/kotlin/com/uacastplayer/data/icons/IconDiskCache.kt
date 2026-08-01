package com.uacastplayer.data.icons

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.icons.CacheEntry
import com.uacastplayer.icons.IconCacheTrimmer
import com.uacastplayer.icons.ImageFormatDetector
import com.uacastplayer.log.AppLog
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "IconDiskCache"

/**
 * Raw validated icon bytes, one file per SHA-256(url), evicted LRU-style once over 256MB or
 * 20,000 files. This is deliberately separate from Coil's own disk cache (Stage 5 doc): this
 * cache is our source of truth for "do we already have this icon"; Coil's cache is just for its
 * own decoded-bitmap bookkeeping once it loads a file from here.
 */
class IconDiskCache(context: Context) {

    private val directory = File(context.filesDir, "icon_cache").apply { mkdirs() }

    private fun fileFor(url: String): File = File(directory, Fingerprint.of(url))

    suspend fun get(url: String): File? = withContext(Dispatchers.IO) {
        val file = fileFor(url)
        if (!file.isFile) return@withContext null
        file.setLastModified(System.currentTimeMillis())
        file
    }

    /**
     * Writes to a *unique* temp file, then publishes it with a rename.
     *
     * The temp name used to be derived from the url, which made it the same file for concurrent
     * writers of the same icon - and that is a routine case, not a rare one: [IconPrefetcher] runs
     * six fetches at a time, and a provider listing "Channel HD"/"Channel SD"/"Channel FHD" gives
     * them all one `tvg-logo` to fetch. One coroutine's write landed in the middle of another's,
     * and the rename published the truncated result as a valid cached icon.
     *
     * Returns null when the write or the publish fails, rather than a [File] that does not exist -
     * callers treat a non-null result as a resolved icon.
     */
    suspend fun put(url: String, bytes: ByteArray): File? = withContext(Dispatchers.IO) {
        if (bytes.size > MAX_ICON_BYTES) return@withContext null
        if (ImageFormatDetector.detect(bytes) == null) return@withContext null

        val file = fileFor(url)
        val temp = runCatching { File.createTempFile(file.name, TEMP_SUFFIX, directory) }.getOrNull()
            ?: return@withContext null
        try {
            temp.writeBytes(bytes)
            if (publish(temp, file)) file else null
        } catch (e: IOException) {
            // Worth a line now that a failure here is the difference between a cached icon and
            // none: a full disk or a revoked directory shows up as icons that silently never stick.
            // The exception's class only - never the url, which is the icon host's, not ours.
            AppLog.w(TAG) { "Icon cache write failed: ${e.javaClass.simpleName}" }
            null
        } finally {
            // A no-op once publish() has moved it; the cleanup that matters when it hasn't.
            temp.delete()
        }
    }

    /** Concurrent writers of the same url now hold distinct temp files, so whichever renames last
     * simply replaces an identical, complete file. The retry covers filesystems that refuse a
     * rename onto an existing target rather than replacing it. */
    private fun publish(temp: File, target: File): Boolean {
        if (temp.renameTo(target)) return true
        target.delete()
        return temp.renameTo(target)
    }

    suspend fun trim() = withContext(Dispatchers.IO) {
        val all = directory.listFiles { f -> f.isFile } ?: return@withContext
        val (temps, cached) = all.partition { it.name.endsWith(TEMP_SUFFIX) }

        // Leftovers from a crash mid-write or a failed publish. This method used to filter them out
        // of its own listing, so they were never deleted AND never counted toward the size budget
        // it enforces - an unbounded hole in exactly the accounting it exists to do. Only stale ones
        // go: a temp file younger than the cutoff may be an in-flight put() from another coroutine.
        val now = System.currentTimeMillis()
        temps.filter { now - it.lastModified() > STALE_TEMP_AGE_MILLIS }.forEach { it.delete() }

        val entries = cached.map { CacheEntry(it.name, it.length(), it.lastModified()) }
        val toEvict = IconCacheTrimmer.selectEntriesToEvict(entries).map { it.key }.toSet()
        for (file in cached) {
            if (file.name in toEvict) file.delete()
        }
    }

    companion object {
        const val MAX_ICON_BYTES = 5 * 1024 * 1024

        @VisibleForTesting
        internal const val TEMP_SUFFIX = ".tmp"

        /** Generous next to the seconds a real write takes, so this can never delete a temp file
         * another coroutine is still writing into. */
        @VisibleForTesting
        internal const val STALE_TEMP_AGE_MILLIS = 60L * 60L * 1000L
    }
}
