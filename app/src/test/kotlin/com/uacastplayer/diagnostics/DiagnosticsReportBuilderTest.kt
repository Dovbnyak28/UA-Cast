package com.uacastplayer.diagnostics

import com.uacastplayer.data.prefs.BufferSize
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.log.LogEntry
import com.uacastplayer.log.LogLevel
import com.uacastplayer.performance.DeviceTier
import com.uacastplayer.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportBuilderTest {

    private fun sampleSnapshot(logEntries: List<LogEntry> = emptyList()) = DiagnosticsSnapshot(
        appVersionName = "1.2.3",
        deviceModel = "Pixel Test",
        androidApiLevel = 34,
        deviceTier = DeviceTier.MID_RANGE,
        bufferSize = BufferSize.MEDIUM,
        iconDisplayMode = IconDisplayMode.CACHE,
        appTheme = AppTheme.AZURE,
        usedMemoryBytes = 100L * 1024 * 1024,
        totalMemoryBytes = 200L * 1024 * 1024,
        maxMemoryBytes = 512L * 1024 * 1024,
        logEntries = logEntries,
        remuxEffectiveness = RemuxEffectivenessCounts(remuxAttempted = 4, remuxPlaying = 3, remuxFailed = 1),
    )

    @Test
    fun `report includes every field of the snapshot`() {
        val report = DiagnosticsReportBuilder.build(sampleSnapshot())

        assertTrue(report.contains("1.2.3"))
        assertTrue(report.contains("Pixel Test"))
        assertTrue(report.contains("34"))
        assertTrue(report.contains("MID_RANGE"))
        assertTrue(report.contains("MEDIUM"))
        assertTrue(report.contains("CACHE"))
        assertTrue(report.contains("AZURE"))
        assertTrue(report.contains("100MB"))
        assertTrue(report.contains("200MB"))
        assertTrue(report.contains("512MB"))
        assertTrue(report.contains("Proxy+remux: 4/3/1"))
    }

    @Test
    fun `report includes log entries verbatim in order`() {
        val entries = listOf(
            LogEntry(LogLevel.DEBUG, "TagA", "first message", 1L),
            LogEntry(LogLevel.ERROR, "TagB", "second message", 2L),
        )

        val report = DiagnosticsReportBuilder.build(sampleSnapshot(entries))

        val firstIndex = report.indexOf("first message")
        val secondIndex = report.indexOf("second message")
        assertTrue(firstIndex >= 0 && secondIndex > firstIndex)
        assertTrue(report.contains("[DEBUG] TagA: first message"))
        assertTrue(report.contains("[ERROR] TagB: second message"))
    }

    @Test
    fun `empty log entries still produce a well-formed report`() {
        val report = DiagnosticsReportBuilder.build(sampleSnapshot(emptyList()))

        assertTrue(report.contains("Recent log entries (0):"))
    }
}
