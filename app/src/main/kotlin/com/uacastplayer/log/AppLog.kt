package com.uacastplayer.log

import android.util.Log
import com.uacastplayer.BuildConfig

/**
 * Thin logging wrapper that GUARANTEES no raw URL, credential, or token reaches [LogBuffer] or
 * Logcat - every [message] is passed through [LogSanitizer] before either sink sees it. That
 * guarantee is enforced here, in the wrapper itself, precisely so a careless call site (e.g.
 * `AppLog.d { "loading $url" }`) can't bypass it: the wrapper is the only path to both sinks, so
 * there is nowhere for an unsanitized value to leak from. The message is always recorded into
 * [LogBuffer] (so a release build's "Send diagnostics" report has something to show); only the
 * real Logcat write, which would otherwise spam a shipped device's system log for no one to read,
 * stays debug-only.
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
        if (BuildConfig.DEBUG) Log.e(tag, text, throwable)
    }
}
