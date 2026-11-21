package com.uacastplayer.log

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The crash record, which only ever matters once - on the launch after the crash it describes.
 *
 * Two of these are the reason it is worth having at all. The stack trace has to survive a process
 * that is dying, and it has to be as safe to hand to someone as every other line this app writes:
 * an exception message routinely names the host, and sometimes the credentials, of a stream the
 * user pays for.
 */
class CrashLogTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        // Cleared so `install` chains onto nothing: the JVM's own handler would otherwise print
        // every deliberately-crashed test to stderr.
        Thread.setDefaultUncaughtExceptionHandler(null)
        LogBuffer.clear()
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        CrashLog.clear()
        LogBuffer.clear()
    }

    private fun install() = CrashLog.install(
        filesDir = folder.root,
        appVersionName = "0.9.0",
        deviceDescription = "Xiaomi Mi A2 (API 30)",
    )

    /** Simulates the crash by handing the installed handler an exception, which is exactly what the
     * runtime does - without taking the test JVM down with it. */
    private fun crashWith(error: Throwable) {
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)
    }

    @Test
    fun anAppThatHasNeverCrashedHasNothingToShow() {
        install()

        assertNull(CrashLog.read())
    }

    @Test
    fun aCrashIsRecordedWithTheTraceAndTheBuildItHappenedOn() {
        install()

        crashWith(IllegalStateException("player was released twice"))

        val report = CrashLog.read()!!
        assertTrue(report.contains("0.9.0"))
        assertTrue(report.contains("Xiaomi Mi A2 (API 30)"))
        assertTrue(report.contains("IllegalStateException"))
        assertTrue(report.contains("player was released twice"))
        // The frames, not just the message - a trace without them names the symptom and not the site.
        assertTrue(report.contains("at com.uacastplayer.log.CrashLogTest"))
    }

    /**
     * The reason the trace goes through [LogSanitizer] rather than straight to the file: a stack
     * trace is not exempt from the app's redaction rule just because the JVM wrote it, and this is
     * the exact shape of the exception a dead stream produces.
     *
     * What is asserted is [LogSanitizer]'s actual contract, which keeps the host and drops
     * everything after it. That is a deliberate trade the sanitizer's own doc spells out - the host
     * is the diagnosis ("which provider"), the path and the query are the secret - and this test
     * exists to notice if a crash report ever stops honouring it.
     */
    @Test
    fun aStreamUrlInsideTheExceptionMessageIsRedacted() {
        install()

        crashWith(
            RuntimeException("failed to open https://tv.example-provider.net/live/8891?token=abcdefghijklmnop"),
        )

        val report = CrashLog.read()!!
        assertFalse("the stream path must not survive into a shareable report", report.contains("/live/8891"))
        assertFalse("the token must not survive", report.contains("abcdefghijklmnop"))
        // Still recognisable as that failure, or the redaction has cost the diagnosis.
        assertTrue(report.contains("RuntimeException"))
        assertTrue(report.contains("tv.example-provider.net"))
    }

    /**
     * The other half of that trade, found on a real crash rather than reasoned about: frame lines
     * are machine-generated and must survive intact. Sanitising them too cost the *method name* of
     * every frame whose identifier ran past the sanitizer's token threshold, leaving
     * `at android.app.ActivityThread.<token:675806>(ActivityThread.java:2595)`.
     */
    @Test
    fun frameLinesKeepTheirMethodNames() {
        install()

        crashWith(IllegalStateException("boom"))

        val report = CrashLog.read()!!
        assertFalse("a frame's method name must not be redacted", report.contains("<token:"))
        assertTrue(report.contains("frameLinesKeepTheirMethodNames"))
    }

    /** A trace says where it died; the log tail says what it was doing. */
    @Test
    fun theRecentLogEntriesAreKeptAlongsideTheTrace() {
        install()
        LogBuffer.record(LogLevel.WARN, "DlnaSessionRepository", "SOAP Play refused: HTTP 701")

        crashWith(IllegalStateException("boom"))

        assertTrue(CrashLog.read()!!.contains("SOAP Play refused: HTTP 701"))
    }

    /**
     * Chaining, not replacing. The handler that was there is the one that shows "app has stopped"
     * and ends the process - swallowing it would leave a crashed app frozen on its last frame,
     * which is a worse failure than the crash.
     */
    @Test
    fun theHandlerThatWasThereBeforeStillRuns() {
        var chained = 0
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> chained++ }
        install()

        crashWith(IllegalStateException("boom"))

        assertTrue(CrashLog.read() != null)
        assertTrue("the previous handler must still terminate the process", chained == 1)
    }

    @Test
    fun clearingRemovesTheRecord() {
        install()
        crashWith(IllegalStateException("boom"))

        CrashLog.clear()

        assertNull(CrashLog.read())
    }

    /** Only the latest is kept, and it must actually replace the previous one rather than append. */
    @Test
    fun asecondCrashReplacesTheFirst() {
        install()
        crashWith(IllegalStateException("the first one"))

        crashWith(IllegalArgumentException("the second one"))

        val report = CrashLog.read()!!
        assertTrue(report.contains("the second one"))
        assertFalse(report.contains("the first one"))
    }

    @Test
    fun aPathologicalThrowableIsTruncatedToABoundedReport() {
        install()

        crashWith(IllegalStateException("x".repeat(100_000)))

        val report = CrashLog.read()!!
        assertTrue(report.contains("[stack trace truncated]"))
        assertTrue(report.length < 70_000)
    }
}
