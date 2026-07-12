package com.uacastplayer.log

import android.util.Log
import com.uacastplayer.BuildConfig

/**
 * Thin logging wrapper that compiles away to a no-op in release builds and never accepts raw
 * URLs or other sensitive values directly - callers pass a lazy [message] so the string is only
 * built when logging is actually enabled.
 */
object AppLog {

    inline fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag, message())
    }

    inline fun w(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.w(tag, message())
    }

    inline fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (BuildConfig.DEBUG) Log.e(tag, message(), throwable)
    }
}
