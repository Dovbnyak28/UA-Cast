package com.uacastplayer.log

import com.uacastplayer.core.concurrent.runCatchingNonFatal
import java.io.File
import java.io.PrintWriter
import java.io.Writer

private const val TAG = "CrashLog"
private const val CRASH_FILE_NAME = "last_crash.txt"

/**
 * Records the stack trace of a crash to a file, so the next launch has something to show.
 *
 * This exists because of what "it crashed" is worth without one. The app already keeps a rolling
 * [LogBuffer] and can format a diagnostics report from it, but a process that dies takes that
 * buffer with it - so the single most useful thing a user could send was also the one thing that
 * never survived. This writes it down before the process goes.
 *
 * **Not telemetry.** Nothing is uploaded, nothing is sent, no network is touched. The file sits in
 * the app's private storage until the user chooses to include it in a diagnostics report, and
 * `Clear` removes it. That is the same bargain the rest of this app makes: the data is theirs, and
 * sharing it is an action they take.
 *
 * Only the most recent crash is kept. A history would need rotation, a size budget and a UI to
 * browse it, and the crash that matters is almost always the one that just happened.
 */
object CrashLog {

    /**
     * How much of the rolling log to keep alongside the trace.
     *
     * A stack trace says where the app died; these say what it was doing. Bounded because this is
     * written by a process that is already dying - a handler that spent a second serialising
     * everything would turn a crash into a crash plus an ANR.
     */
    private const val LOG_TAIL_ENTRIES = 60
    private const val MAX_STACK_TRACE_CHARS = 64_000

    @Volatile private var crashFile: File? = null

    /**
     * Installs the handler. Call once, from `Application.onCreate`.
     *
     * The previous handler is kept and always invoked afterwards - it is the one that shows the
     * system's "app has stopped" dialog and terminates the process. Replacing it rather than
     * chaining would leave a crashed app frozen on its last frame, which is a worse failure than
     * the crash.
     */
    fun install(filesDir: File, appVersionName: String, deviceDescription: String) {
        crashFile = File(filesDir, CRASH_FILE_NAME)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            write(thread, error, appVersionName, deviceDescription)
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last recorded crash, or null when the app has never crashed (or the record was cleared). */
    fun read(): String? = crashFile?.takeIf { it.isFile }?.let { file ->
        runCatchingNonFatal { file.readText() }.getOrNull()
    }

    fun clear() {
        crashFile?.let { file -> runCatchingNonFatal { file.delete() } }
    }

    /**
     * Everything here is wrapped, and failures are swallowed on purpose.
     *
     * This runs inside the uncaught-exception handler of a process that is already going down. An
     * exception thrown from here would replace the real crash with a meaningless one and lose the
     * trace that was worth keeping - so a full disk or a revoked directory costs the report, not
     * the diagnosis.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun write(thread: Thread, error: Throwable, appVersionName: String, deviceDescription: String) {
        val file = crashFile ?: return
        try {
            file.writeText(format(thread, error, appVersionName, deviceDescription))
        } catch (e: Exception) {
            // Not AppLog: that writes to LogBuffer, which is about to be lost anyway, and this is
            // the one place where being unable to log must not become a second failure.
            android.util.Log.w(TAG, "could not record the crash: ${e.javaClass.simpleName}")
        }
    }

    private fun format(
        thread: Thread,
        error: Throwable,
        appVersionName: String,
        deviceDescription: String,
    ): String = buildString {
        appendLine("UA Cast crash report")
        appendLine("App version: $appVersionName")
        appendLine("Device: $deviceDescription")
        appendLine("Thread: ${thread.name}")
        appendLine()
        appendLine(stackTraceOf(error))
        appendLine()
        val tail = LogBuffer.snapshot().takeLast(LOG_TAIL_ENTRIES)
        appendLine("Last $LOG_TAIL_ENTRIES log entries before the crash (${tail.size} available):")
        tail.forEach { entry -> appendLine("[${entry.level}] ${entry.tag}: ${entry.message}") }
    }

    /**
     * Sanitised - but only the lines that can carry anything worth hiding.
     *
     * Exception *messages* routinely carry exactly what this app keeps out of a shared report:
     * `UnknownHostException: tv.example-provider.net`, an IOException naming a stream URL with
     * credentials in it. Those go through [LogSanitizer], the same guarantee [AppLog] gives every
     * log line.
     *
     * Frame lines do not. The JVM builds them from class metadata, so they cannot contain user
     * data - and running them through the sanitizer anyway was actively harmful: a long method name
     * is a 24-character run of token characters, so `TOKEN_REGEX` replaced it with a marker.
     * Measured on a real crash, which is how this was found:
     * `at android.app.ActivityThread.<token:675806>(ActivityThread.java:2595)`. Class and line
     * survived, the method name did not, and a redaction that costs the diagnosis while protecting
     * nothing is the wrong trade.
     */
    private fun stackTraceOf(error: Throwable): String {
        val writer = BoundedTraceWriter(MAX_STACK_TRACE_CHARS)
        PrintWriter(writer).use(error::printStackTrace)
        val sanitized = writer.toString()
            .lineSequence()
            .joinToString("\n") { line -> if (line.isFrameLine()) line else LogSanitizer.sanitize(line) }
        return if (writer.truncated) "$sanitized\n[stack trace truncated]" else sanitized
    }

    /** `\tat com.example.Thing.method(Thing.kt:42)`, and the `... 3 more` elision that follows a
     * cause. Both are machine-generated; everything else in a trace came from a person or an
     * exception message. */
    private fun String.isFrameLine(): Boolean {
        val body = trimStart()
        return body.startsWith("at ") || body.startsWith("... ")
    }
}

/** Writer used by the uncaught-exception path so a huge message/cause chain cannot grow a report
 * without a bound while the process is already failing. */
private class BoundedTraceWriter(private val maxChars: Int) : Writer() {
    private val builder = StringBuilder(maxChars)
    var truncated: Boolean = false
        private set

    override fun write(cbuf: CharArray, off: Int, len: Int) {
        if (len <= 0 || builder.length >= maxChars) {
            if (len > 0) truncated = true
            return
        }
        val accepted = minOf(len, maxChars - builder.length)
        builder.append(cbuf, off, accepted)
        if (accepted < len) truncated = true
    }

    override fun flush() = Unit

    override fun close() = Unit

    override fun toString(): String = builder.toString()
}
