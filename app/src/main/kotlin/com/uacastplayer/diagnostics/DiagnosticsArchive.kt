package com.uacastplayer.diagnostics

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.log.AppLog
import com.uacastplayer.log.LogcatReader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "DiagnosticsArchive"

/**
 * The diagnostics report as a file the user's mail app can attach.
 *
 * The report used to be the body of the email and nothing else, which capped it at what a person
 * will scroll past and at what a mail client will not mangle. It also meant the only log in it was
 * this app's own in-memory buffer - so a report sent after restarting the app carried three lines
 * and said nothing.
 *
 * A file lifts both limits at once: the body stays the short readable summary, and the attachment
 * carries the same summary plus the whole recent log of the process, which is where media3, the
 * Cast SDK and OkHttp write the things that actually explain a failure.
 *
 * Written to `cacheDir`, deliberately. It is a copy made to be sent, the system may reclaim it, and
 * nothing should be keeping half-megabyte logs in the app's permanent storage.
 */
object DiagnosticsArchive {

    private const val DIRECTORY = "diagnostics"
    private const val AUTHORITY_SUFFIX = ".diagnostics"

    /** Keep a small bounded history without deleting a report that a mail/share client may still
     * be reading. */
    private fun clearPrevious(directory: File) {
        val keep = MAX_REPORT_FILES - 1
        directory.listFiles()
            ?.asSequence()
            ?.filter(File::isFile)
            ?.sortedByDescending(File::lastModified)
            ?.drop(keep)
            ?.forEach { file -> runCatchingNonFatal { file.delete() } }
    }

    /**
     * Writes [report] plus this process's log to a file and returns a `content://` URI for it, or
     * null when the file could not be written.
     *
     * Null is a degraded send, not a failed one - the caller still has the report for the body, and
     * an email with a summary and no attachment beats no email at all.
     */
    @Suppress("TooGenericExceptionCaught")
    fun write(context: Context, report: String): Uri? = writeReportFile(context, report)?.let { file ->
        try {
            FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        } catch (e: Exception) {
            AppLog.w(TAG) { "Could not expose the diagnostics file: ${e.javaClass.simpleName}" }
            null
        }
    }

    /** Writes the cache file independently of Android's provider registry for deterministic tests. */
    @Suppress("TooGenericExceptionCaught")
    internal fun writeReportFile(context: Context, report: String): File? = try {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        clearPrevious(directory)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        // Reports can be generated twice in the same second (for example, after a failed share).
        // A short random suffix makes each URI immutable and prevents one writer from replacing a
        // file that another process is still attaching.
        val file = File(directory, "ua-cast-log-$stamp-${UUID.randomUUID().toString().take(8)}.txt")

        val logcat = LogcatReader.read()
        file.writeText(
            buildString {
                append(report)
                appendLine()
                appendLine()
                if (logcat == null) {
                    // Said out loud rather than left as an absence: "the log is missing" and "the
                    // log is empty" are different problems, and only one of them is the app's.
                    appendLine("--- Full log unavailable on this device ---")
                } else {
                    appendLine("--- Full log for this process ---")
                    appendLine(logcat)
                    appendLine("--- End of log ---")
                }
            },
        )
        file
    } catch (e: Exception) {
        AppLog.w(TAG) { "Could not write the diagnostics file: ${e.javaClass.simpleName}" }
        null
    }

    private const val MAX_REPORT_FILES = 4
}
