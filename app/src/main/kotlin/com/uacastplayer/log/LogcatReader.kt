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
 * Known vendor spam is dropped before the line budget is applied - see [LogcatNoisePolicy], where
 * the measurement is, and which explains why a filter is worth having at all when the device's own
 * ring buffer is the thing that overflowed.
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

    /** A second bound, on characters rather than lines: one stack trace or one dumped manifest can be
     * longer than everything around it. */
    private const val MAX_CHARS = 512 * 1024

    /**
     * The recent log for this process, oldest first, or null if the platform refused.
     *
     * Refusal is ordinary rather than exceptional - some ROMs restrict the binary, and an emulator
     * without it exists - so the caller keeps whatever else it had rather than failing the report.
     */
    @Suppress("TooGenericExceptionCaught")
    fun read(): String? {
        var process: java.lang.Process? = null
        return try {
            val runningProcess = ProcessBuilder("logcat", "-d", "-v", "time", "--pid=${Process.myPid()}")
                .redirectErrorStream(true)
                .start()
            process = runningProcess
            var noiseDropped = 0
            val tail = BoundedLogTail(MAX_LINES, MAX_CHARS)
            runningProcess.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    // Before the budget, not after: dropping these afterwards would leave the same
                    // 23 seconds of log, just shorter. See LogcatNoisePolicy for what was measured.
                    if (LogcatNoisePolicy.isNoise(line)) {
                        noiseDropped++
                    } else {
                        tail.add(LogSanitizer.sanitize(line))
                    }
                }
            }
            val body = tail.contentOrNull()
            // Said at the top, before anything is read: a reader who finds a gap must be able to tell
            // "the app removed this" from "the device never recorded it". Only the second is a problem.
            body?.let { if (noiseDropped == 0) it else "($noiseDropped lines of device noise omitted)\n$it" }
        } catch (e: Exception) {
            AppLog.w("LogcatReader") { "Cannot read this process's log: ${e.javaClass.simpleName}" }
            null
        } finally {
            process?.destroy()
        }
    }
}
