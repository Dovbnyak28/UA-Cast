package com.uacastplayer.data

import androidx.core.util.AtomicFile
import com.uacastplayer.log.AppLog
import com.uacastplayer.data.cache.CachePaths
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException

/**
 * Writes through [file]'s [AtomicFile] protocol: run [write] against a fresh write stream, finish
 * the write on success, release the temp file on any failure. Returns false if the write failed.
 *
 * **Reports failures; propagates cancellation.** Stores built on this are driven by fire-and-forget
 * `scope.launch` - starring a channel, locking one, hiding a group, caching a loaded playlist - and
 * an uncaught throw inside a `launch` reaches the thread's default handler and kills the app. A
 * `SupervisorJob` does not change that; it only stops siblings from being cancelled. So each of
 * those stores turned a full disk into a crash. Every one of them holds cache or convenience state,
 * and the caller has already updated its in-memory copy, so a lost write costs that one change at
 * next launch - the right price where the alternative is taking the app down.
 *
 * The catch is broad on purpose: [AtomicFile] requires `failWrite()` after *any* mid-write failure
 * to release its temp file, not just after an [IOException].
 *
 * [AtomicFile.startWrite] is guarded separately, and it has to be: it opens the file, so it is the
 * step a full disk fails at first, and it throws [IOException] of its own accord when it cannot
 * create the file or the directory holding it. Being the first line, it sat *outside* the `try`
 * below - so the one failure this whole function exists to survive was the one it did not catch,
 * and every store listed above still took the app down through it. There is no temp file to
 * release on this path: `startWrite` cleans up after itself before throwing.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun AtomicFile.writeSafely(tag: String, what: String, write: (OutputStream) -> Unit): Boolean {
    val stream = try {
        startWrite()
    } catch (e: IOException) {
        AppLog.w(tag) { "$what could not be opened for writing: ${e.javaClass.simpleName}" }
        return false
    }
    return try {
        write(stream)
        // finishWrite also logs (and hides) sync errors. Observe one successful sync before it
        // can commit; its second sync has no intervening writes and normally no dirty data.
        stream.fd.sync()
        finishWrite(stream)
        // AndroidX logs a failed rename rather than throwing. Never report an uncommitted
        // migration as saved: its caller may retire the only remaining legacy copy.
        if (!baseFile.isFile || File(baseFile.path + CachePaths.ATOMIC_WRITE_SUFFIX).exists()) {
            throw IOException("Atomic write was not committed")
        }
        true
    } catch (e: Exception) {
        failWrite(stream)
        if (e is CancellationException) throw e
        AppLog.w(tag) { "$what write failed: ${e.javaClass.simpleName}" }
        false
    }
}
