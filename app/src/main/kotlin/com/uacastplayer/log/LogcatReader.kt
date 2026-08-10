package com.uacastplayer.log

import android.os.Process

/**
 * This process's own logcat, sanitized.
 *
 * [LogBuffer] only ever held what *this app* logged through [AppLog], in memory, for the life of
 * one process. Two consequences met in the same report: a user who restarts the app before sending
 * a report sends an empty one - three lines, which is what "the log looks like a dud" means - and
 * even a full buffer says nothing about the parts that actually fail. Playback problems live in
 * media3, casting problems in the Cast SDK, network problems in OkHttp, and none of them log
 * through `AppLog`.
 *
 * An app may always read its own logcat without any permission; `--pid` is what keeps it to that.
 * Nothing here can see another app's output, and asking for `READ_LOGS` - which could - is exactly
 * the permission this app should never hold.
 *
 * **Every line goes through [LogSanitizer] before it is returned**, and that is not a formality
 * here as it is in [AppLog]. `AppLog` sanitizes at the one door into its own buffer; this reads
 * lines written by libraries that know nothing about that rule, and media3 logs stream URLs -
 * which for an Xtream playlist carry the user's username and password in the query string.
 */
object LogcatReader {

    /** Enough to cover the minutes before a problem was noticed, short of turning an email into a
     * file nobody opens. A stalled stream retrying logs a few lines a second. */
    private const val MAX_LINES = 4_000

    /** A second bound, on bytes rather than lines: one stack trace or one dumped manifest can be
     * longer than everything around it. */
    private const val MAX_CHARS = 512 * 1024

    /**
     * The recent log for this process, oldest first, or null if the platform refused.
     *
     * Refusal is ordinary rather than exceptional - some ROMs restrict the binary, and an emulator
     * without it exists - so the caller keeps whatever else it had rather than failing the report.
     */
    @Suppress("TooGenericExceptionCaught")
    fun read(): String? = try {
        val process = ProcessBuilder("logcat", "-d", "-v", "time", "--pid=${Process.myPid()}")
            .redirectErrorStream(true)
            .start()
        val lines = process.inputStream.bufferedReader().use { reader ->
            reader.lineSequence().map(LogSanitizer::sanitize).toList()
        }
        process.destroy()
        lines.takeLast(MAX_LINES)
            .joinToString("\n")
            .takeLast(MAX_CHARS)
            .ifBlank { null }
    } catch (e: Exception) {
        AppLog.w("LogcatReader") { "Cannot read this process's log: ${e.javaClass.simpleName}" }
        null
    }
}
