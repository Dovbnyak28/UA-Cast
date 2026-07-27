package com.uacastplayer.diagnostics

import com.uacastplayer.data.prefs.BufferSize
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.log.LogEntry
import com.uacastplayer.performance.DeviceTier
import com.uacastplayer.ui.theme.AppTheme

/** Everything the diagnostics report needs, gathered by the caller (see
 * [com.uacastplayer.AppViewModel.buildDiagnosticsReport]) so the formatting itself stays a plain,
 * Android-free function. */
data class DiagnosticsSnapshot(
    val appVersionName: String,
    val deviceModel: String,
    val androidApiLevel: Int,
    val deviceTier: DeviceTier,
    val bufferSize: BufferSize,
    val iconDisplayMode: IconDisplayMode,
    val appTheme: AppTheme,
    val usedMemoryBytes: Long,
    val totalMemoryBytes: Long,
    val maxMemoryBytes: Long,
    val logEntries: List<LogEntry>,
)

/** Formats a [DiagnosticsSnapshot] into the plain-text report shared from HelpScreen's "Send
 * diagnostics" button. Never includes anything beyond what's already in the snapshot - in
 * particular, [LogEntry] messages are included verbatim: they're already guaranteed free of raw
 * URLs/tokens/credentials by [com.uacastplayer.log.LogSanitizer], which every
 * [com.uacastplayer.log.AppLog] call runs through before an entry ever reaches the buffer this
 * report reads from. */
object DiagnosticsReportBuilder {

    private const val BYTES_PER_MB = 1024 * 1024

    fun build(snapshot: DiagnosticsSnapshot): String = buildString {
        appendLine("UA Cast diagnostics report")
        appendLine("App version: ${snapshot.appVersionName}")
        appendLine("Device: ${snapshot.deviceModel} (Android API ${snapshot.androidApiLevel})")
        appendLine("Device tier: ${snapshot.deviceTier}")
        appendLine("Buffer size: ${snapshot.bufferSize}")
        appendLine("Icon display mode: ${snapshot.iconDisplayMode}")
        appendLine("App theme: ${snapshot.appTheme}")
        appendLine(
            "Memory: ${snapshot.usedMemoryBytes.toMb()}MB used / " +
                "${snapshot.totalMemoryBytes.toMb()}MB total / ${snapshot.maxMemoryBytes.toMb()}MB max",
        )
        appendLine()
        appendLine("Recent log entries (${snapshot.logEntries.size}):")
        snapshot.logEntries.forEach { entry ->
            appendLine("[${entry.level}] ${entry.tag}: ${entry.message}")
        }
    }

    private fun Long.toMb(): Long = this / BYTES_PER_MB
}
