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
    val remuxEffectiveness: RemuxEffectivenessCounts,
    /** The last crash this device recorded, or null if it has never had one - see
     * [com.uacastplayer.log.CrashLog]. Placed at the top of the report when present, because a
     * report that contains one is almost always being sent *about* it. */
    val lastCrash: String? = null,

    /** When the report was made, and how long the app had been running. Together they say whether
     * the log below can even contain the moment being reported: a report generated four seconds
     * after launch, about something that happened yesterday, holds nothing about it. */
    val generatedAtMillis: Long = 0L,
    val uptimeMillis: Long = 0L,

    /** UI language, because four are shipped and a wrong string is reported as "the text is
     * wrong", never as "the Spanish plural rule is wrong". */
    val language: String = "?",

    /** How big the loaded playlist is. "The app is slow" and "scrolling stutters" are answered by
     * this line and the device tier together far more often than by anything in the log. */
    val channelCount: Int = 0,
    val groupCount: Int = 0,

    /** Whether a guide is loaded, how much of it, and whether it was cut short. A truncated guide
     * is the whole explanation of "the TV guide is missing my channels", and the app already knows
     * it was truncated - it simply never said so anywhere a user could forward. */
    val epgChannelCount: Int? = null,
    val epgProgrammeCount: Int? = null,
    val epgTruncated: Boolean = false,
    val epgSource: String = "?",

    /** Wi-Fi, mobile, or nothing - and whether the system considers it metered. Buffering reports
     * are unanswerable without it, and it costs no permission to read. */
    val network: String = "?",

    /** Free space where the caches live. A disk with nothing left is why a snapshot did not
     * persist, and it presents as "it forgets my playlist", never as a storage complaint. */
    val freeStorageBytes: Long = 0L,

    /** Whether something was being cast when the report was made - the local player behaves
     * deliberately differently then, and a reader who does not know that misreads the whole log. */
    val casting: Boolean = false,
)

/** Formats a [DiagnosticsSnapshot] into the plain-text report shared from HelpScreen's "Send
 * diagnostics" button. Never includes anything beyond what's already in the snapshot - in
 * particular, [LogEntry] messages are included verbatim: they're already guaranteed free of raw
 * URLs/tokens/credentials by [com.uacastplayer.log.LogSanitizer], which every
 * [com.uacastplayer.log.AppLog] call runs through before an entry ever reaches the buffer this
 * report reads from. */
object DiagnosticsReportBuilder {

    private const val BYTES_PER_MB = 1024 * 1024
    private const val MILLIS_PER_SECOND = 1000
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600

    private val TIMESTAMP = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun build(snapshot: DiagnosticsSnapshot): String = buildString {
        appendLine("UA Cast diagnostics report")
        appendLine(
            "Generated: ${snapshot.generatedAtMillis.asTimestamp()} " +
                "(app running ${snapshot.uptimeMillis.asDuration()})",
        )
        appendLine("App version: ${snapshot.appVersionName}")
        appendLine("Device: ${snapshot.deviceModel} (Android API ${snapshot.androidApiLevel})")
        appendLine("Device tier: ${snapshot.deviceTier}")
        appendLine("Buffer size: ${snapshot.bufferSize}")
        appendLine("Icon display mode: ${snapshot.iconDisplayMode}")
        appendLine("App theme: ${snapshot.appTheme} | Language: ${snapshot.language}")
        appendLine("Network: ${snapshot.network}")
        appendLine("Free storage: ${snapshot.freeStorageBytes.toMb()}MB")
        appendLine("Playlist: ${snapshot.channelCount} channels in ${snapshot.groupCount} groups")
        appendLine("EPG: " + epgLine(snapshot))
        if (snapshot.casting) appendLine("Casting: yes - the local player is stopped on purpose")
        appendLine(
            "Memory: ${snapshot.usedMemoryBytes.toMb()}MB used / " +
                "${snapshot.totalMemoryBytes.toMb()}MB total / ${snapshot.maxMemoryBytes.toMb()}MB max",
        )
        snapshot.lastCrash?.let { crash ->
            appendLine()
            appendLine("--- Last recorded crash ---")
            appendLine(crash.trimEnd())
            appendLine("--- End of crash ---")
        }
        appendLine()
        appendLine("Routing effectiveness (attempted/reached PLAYING/failed):")
        val r = snapshot.remuxEffectiveness
        appendLine("  Direct: ${r.directAttempted}/${r.directPlaying}/${r.directFailed}")
        appendLine("  Proxy+remux: ${r.remuxAttempted}/${r.remuxPlaying}/${r.remuxFailed}")
        appendLine("  Proxy rewrite: ${r.proxyRewriteAttempted}/${r.proxyRewritePlaying}/${r.proxyRewriteFailed}")
        appendLine()
        appendLine("Recent log entries (${snapshot.logEntries.size}), newest last:")
        snapshot.logEntries.forEach { entry ->
            // The timestamp is the point of a log. Without it these lines say what happened but not
            // whether two of them were 200ms or two hours apart, which is most of what a reader
            // needs - and LogEntry has carried one all along.
            appendLine("${entry.timestampMillis.asTimestamp()} [${entry.level}] ${entry.tag}: ${entry.message}")
        }
    }

    /** Loaded or not, how much of it, and whether it was cut short - see
     * [com.uacastplayer.epg.EpgTruncation]. */
    private fun epgLine(snapshot: DiagnosticsSnapshot): String {
        val channels = snapshot.epgChannelCount ?: return "not loaded (source ${snapshot.epgSource})"
        val truncation = if (snapshot.epgTruncated) " - TRUNCATED, the guide is incomplete" else ""
        return "$channels channels, ${snapshot.epgProgrammeCount} programmes " +
            "(source ${snapshot.epgSource})$truncation"
    }

    private fun Long.asTimestamp(): String =
        if (this <= 0L) {
            "?"
        } else {
            java.time.Instant.ofEpochMilli(this)
                .atZone(java.time.ZoneId.systemDefault())
                .format(TIMESTAMP)
        }

    private fun Long.asDuration(): String {
        val seconds = this / MILLIS_PER_SECOND
        return when {
            seconds < SECONDS_PER_MINUTE -> "${seconds}s"
            seconds < SECONDS_PER_HOUR ->
                "${seconds / SECONDS_PER_MINUTE}m ${seconds % SECONDS_PER_MINUTE}s"
            else ->
                "${seconds / SECONDS_PER_HOUR}h ${(seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE}m"
        }
    }

    private fun Long.toMb(): Long = this / BYTES_PER_MB
}
