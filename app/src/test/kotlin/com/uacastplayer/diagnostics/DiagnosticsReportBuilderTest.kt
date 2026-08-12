package com.uacastplayer.diagnostics

import com.uacastplayer.data.prefs.BufferSize
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.log.LogEntry
import com.uacastplayer.log.LogLevel
import com.uacastplayer.performance.DeviceTier
import com.uacastplayer.ui.theme.AppTheme
import org.junit.Assert.assertFalse
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

    /**
     * The single most useful thing in a log, and it was being thrown away: [LogEntry] has carried a
     * timestamp all along and the report printed level, tag and message only. Without it a reader
     * cannot tell whether two lines are 200ms apart or two hours - which is most of what they are
     * reading the log to find out.
     */
    @Test
    fun `every log line carries the time it happened`() {
        val at = 1_800_000_000_000L
        val report = DiagnosticsReportBuilder.build(
            sampleSnapshot(logEntries = listOf(LogEntry(LogLevel.DEBUG, "Player", "started", at))),
        )

        assertTrue("the line is there", report.contains("[DEBUG] Player: started"))
        val clockThenLevel = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} \[DEBUG]""")
        assertTrue("and so is a clock time", clockThenLevel.containsMatchIn(report))
    }

    /**
     * A guide that was cut short is the entire explanation of "the TV guide is missing my
     * channels", and the app knew it all along without ever saying so anywhere a user could
     * forward. Shouted rather than mentioned, because a reader skimming a report should not have to
     * compare two numbers to notice it.
     */
    @Test
    fun `a truncated guide says so in words`() {
        val report = DiagnosticsReportBuilder.build(
            sampleSnapshot().copy(epgChannelCount = 120, epgProgrammeCount = 4000, epgTruncated = true),
        )

        assertTrue(report.contains("TRUNCATED"))
        assertTrue(report.contains("120 channels"))
    }

    /** No guide at all is a different answer from a small one, and reads as one. */
    @Test
    fun `no guide is not reported as an empty guide`() {
        val report = DiagnosticsReportBuilder.build(sampleSnapshot().copy(epgChannelCount = null))

        assertTrue(report.contains("EPG: not loaded"))
    }

    /**
     * A real report came back reading `EPG: not loaded (source custom)` and nothing anywhere - not
     * in the report, not in the log below it - could say why. The outcome had known: an HTTP code,
     * an exception class, a size refusal. See [com.uacastplayer.data.epg.EpgFailureReason].
     */
    @Test
    fun `a guide that failed says why, not just that it is missing`() {
        val report = DiagnosticsReportBuilder.build(
            sampleSnapshot().copy(
                epgChannelCount = null,
                epgSource = "custom",
                epgFailure = "the server answered HTTP 404",
            ),
        )

        assertTrue(report, report.contains("EPG: not loaded (source custom) - the server answered HTTP 404"))
    }

    /** Nothing tried yet is not a failure, and must not be dressed as one. */
    @Test
    fun `a guide that simply has not loaded yet gains no invented reason`() {
        val report = DiagnosticsReportBuilder.build(
            sampleSnapshot().copy(epgChannelCount = null, epgSource = "custom", epgFailure = null),
        )

        assertTrue(report, report.contains("EPG: not loaded (source custom)"))
        assertFalse(report, report.contains("not loaded (source custom) -"))
    }

    /** Scale answers "the app is slow" more often than the log does. */
    @Test
    fun `the size of the playlist is stated`() {
        val report = DiagnosticsReportBuilder.build(sampleSnapshot().copy(channelCount = 2863, groupCount = 11))

        assertTrue(report.contains("2863 channels in 11 groups"))
    }

    /**
     * A report made four seconds after launch, about something that happened yesterday, contains
     * nothing about it - and that is worth knowing before reading the rest.
     */
    @Test
    fun `the report says when it was made and how long the app had been running`() {
        val report = DiagnosticsReportBuilder.build(
            sampleSnapshot().copy(generatedAtMillis = 1_800_000_000_000L, uptimeMillis = 3_725_000L),
        )

        assertTrue(report.contains("Generated:"))
        assertTrue("uptime in readable units", report.contains("1h 2m"))
    }

    /** While casting the local player is stopped on purpose, and a reader who does not know that
     * misreads every silent line below. */
    @Test
    fun `casting is called out because it changes how the rest reads`() {
        assertTrue(DiagnosticsReportBuilder.build(sampleSnapshot().copy(casting = true)).contains("Casting: yes"))
        assertFalse(DiagnosticsReportBuilder.build(sampleSnapshot()).contains("Casting:"))
    }

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

        // The heading now says which end is newest - a reader should not have to guess whether a
        // log runs forwards or backwards.
        assertTrue(report.contains("Recent log entries (0), newest last:"))
    }

    /**
     * The first field report this app ever received carried three rows of zeros under a heading
     * that never mentioned casting, and they were read as broken counters. They were not: that
     * phone had simply never cast anything. Both halves are fixed here - the heading names what is
     * being counted, and "never" is said in words.
     */
    @Test
    fun `a device that has never cast says so instead of printing nine zeros`() {
        val report = DiagnosticsReportBuilder.build(
            sampleSnapshot().copy(remuxEffectiveness = RemuxEffectivenessCounts()),
        )

        assertTrue("the heading names casting", report.contains("Cast routing effectiveness"))
        assertTrue("and the reason is in words", report.contains("nothing has been cast from this device"))
        assertFalse("with no row of zeros to misread", report.contains("Direct: 0/0/0"))
    }

    @Test
    fun `a device that has cast still gets every route counted`() {
        val report = DiagnosticsReportBuilder.build(sampleSnapshot())

        assertTrue(report.contains("Direct: 0/0/0"))
        assertTrue(report.contains("Proxy+remux: 4/3/1"))
        assertTrue(report.contains("Proxy rewrite: 0/0/0"))
        assertFalse(report.contains("nothing has been cast"))
    }
}
