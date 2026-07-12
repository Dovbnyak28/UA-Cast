package com.uacastplayer.data.icons

import android.content.Context
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.icons.CacheEntry
import com.uacastplayer.icons.IconCacheTrimmer
import com.uacastplayer.icons.ImageFormatDetector
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend fun put(url: String, bytes: ByteArray): File? = withContext(Dispatchers.IO) {
        if (bytes.size > MAX_ICON_BYTES) return@withContext null
        if (ImageFormatDetector.detect(bytes) == null) return@withContext null

        val file = fileFor(url)
        val temp = File(directory, "${file.name}.tmp")
        temp.writeBytes(bytes)
        temp.renameTo(file)
        file
    }

    suspend fun trim() = withContext(Dispatchers.IO) {
        val files = directory.listFiles { f -> f.isFile && !f.name.endsWith(".tmp") } ?: return@withContext
        val entries = files.map { CacheEntry(it.name, it.length(), it.lastModified()) }
        val toEvict = IconCacheTrimmer.selectEntriesToEvict(entries).map { it.key }.toSet()
        for (file in files) {
            if (file.name in toEvict) file.delete()
        }
    }

    companion object {
        const val MAX_ICON_BYTES = 5 * 1024 * 1024
    }
}
