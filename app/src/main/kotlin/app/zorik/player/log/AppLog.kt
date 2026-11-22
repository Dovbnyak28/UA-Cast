package app.zorik.player.log

import android.util.Log
import app.zorik.player.BuildConfig

/**
 * Records messages after [LogSanitizer] redacts recognized URLs and credential patterns. The
 * sanitizer is heuristic, so callers must still avoid including secrets in diagnostic messages.
 * Logcat is debug-only; its exception copy deliberately omits all throwable messages, causes and
 * suppressed exceptions while retaining the type and sanitized top-level stack locations.
 */
object AppLog {

    inline fun d(tag: String, message: () -> String) {
        val text = LogSanitizer.sanitize(message())
        LogBuffer.record(LogLevel.DEBUG, tag, text)
        if (BuildConfig.DEBUG) Log.d(tag, text)
    }

    inline fun w(tag: String, message: () -> String) {
        val text = LogSanitizer.sanitize(message())
        LogBuffer.record(LogLevel.WARN, tag, text)
        if (BuildConfig.DEBUG) Log.w(tag, text)
    }

    inline fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        val text = LogSanitizer.sanitize(message())
        LogBuffer.record(LogLevel.ERROR, tag, text)
        if (BuildConfig.DEBUG) {
            // Exception messages, including causes and suppressed exceptions, can contain provider
            // credentials. Preserve the top-level type and sanitized frame locations, never the
            // original Throwable or its messages.
            val safeThrowable = throwable?.let { original ->
                Throwable(original.javaClass.simpleName).apply {
                    stackTrace = original.stackTrace.map { frame ->
                        StackTraceElement(
                            LogSanitizer.sanitize(frame.className),
                            LogSanitizer.sanitize(frame.methodName),
                            frame.fileName?.let(LogSanitizer::sanitize),
                            frame.lineNumber,
                        )
                    }.toTypedArray()
                }
            }
            Log.e(tag, text, safeThrowable)
        }
    }
}
