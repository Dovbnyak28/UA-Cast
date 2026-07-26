package com.uacastplayer.log

import androidx.annotation.VisibleForTesting
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class LogLevel { DEBUG, WARN, ERROR }

data class LogEntry(val level: LogLevel, val tag: String, val message: String, val timestampMillis: Long)

/**
 * Thread-safe in-memory ring buffer of the app's own recent log entries - the data behind the
 * "Send diagnostics" report (see HelpScreen). Nothing here is persisted or leaves the device
 * unless the user explicitly shares that report; entries are whatever [AppLog]'s callers already
 * pass it, which by existing project convention never includes raw URLs, tokens or credentials.
 *
 * Capped on two axes at once - entry count AND total message length - since either a very chatty
 * session (many small entries) or a handful of unusually large messages could otherwise blow past
 * a cap expressed in only one dimension.
 */
object LogBuffer {

    private const val MAX_ENTRIES = 500
    private const val MAX_TOTAL_CHARS = 256 * 1024

    private val lock = ReentrantLock()
    private val entries = ArrayDeque<LogEntry>()
    private var totalChars = 0

    fun record(level: LogLevel, tag: String, message: String) {
        lock.withLock {
            entries.addLast(LogEntry(level, tag, message, System.currentTimeMillis()))
            totalChars += message.length
            while ((entries.size > MAX_ENTRIES || totalChars > MAX_TOTAL_CHARS) && entries.isNotEmpty()) {
                totalChars -= entries.removeFirst().message.length
            }
        }
    }

    /** Oldest entry first, same order [record] received them. */
    fun snapshot(): List<LogEntry> = lock.withLock { entries.toList() }

    @VisibleForTesting
    fun clear() = lock.withLock {
        entries.clear()
        totalChars = 0
    }
}
