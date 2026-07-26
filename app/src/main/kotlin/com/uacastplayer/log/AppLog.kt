package com.uacastplayer.log

import android.util.Log
import com.uacastplayer.BuildConfig

/**
 * Thin logging wrapper that never accepts raw URLs or other sensitive values directly - callers
 * pass a lazy [message]. The message is always recorded into [LogBuffer] (so a release build's
 * "Send diagnostics" report has something to show); only the real Logcat write, which would
 * otherwise spam a shipped device's system log for no one to read, stays debug-only.
 */
object AppLog {

    inline fun d(tag: String, message: () -> String) {
        val text = message()
        LogBuffer.record(LogLevel.DEBUG, tag, text)
        if (BuildConfig.DEBUG) Log.d(tag, text)
    }

    inline fun w(tag: String, message: () -> String) {
        val text = message()
        LogBuffer.record(LogLevel.WARN, tag, text)
        if (BuildConfig.DEBUG) Log.w(tag, text)
    }

    inline fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        val text = message()
        LogBuffer.record(LogLevel.ERROR, tag, text)
        if (BuildConfig.DEBUG) Log.e(tag, text, throwable)
    }
}
