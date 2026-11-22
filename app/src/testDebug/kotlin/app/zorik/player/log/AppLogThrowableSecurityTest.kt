package app.zorik.player.log

import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class AppLogThrowableSecurityTest {
    @Before fun setup() {
        ShadowLog.clear()
        LogBuffer.clear()
    }

    @After fun clean() {
        ShadowLog.clear()
        LogBuffer.clear()
    }

    @Test fun `debug log never writes exception messages or nested credentials`() {
        val password = "synthetic-password-alpha"
        val token = "synthetic-secret-beta"
        val suppressedSecret = "synthetic-secret-gamma"
        val failure = IOException("top-untrusted-message http://synthetic:$password@example.test/live")
        failure.initCause(IllegalStateException("cause-untrusted-message\ntoken=$token"))
        failure.addSuppressed(IllegalArgumentException("suppressed-untrusted-message password=$suppressedSecret"))

        AppLog.e("AppLogSecurityTest", failure) { "stream failure" }

        val item = ShadowLog.getLogsForTag("AppLogSecurityTest").single()
        val output = item.msg + "\n" + (item.throwable?.stackTraceToString() ?: "")
        listOf(password, token, suppressedSecret).forEach { secret ->
            assertFalse("exception data leaked to debug logcat", output.contains(secret))
            assertFalse("exception data leaked to diagnostics buffer", LogBuffer.snapshot().any {
                it.message.contains(secret)
            })
        }
        listOf("top-untrusted-message", "cause-untrusted-message", "suppressed-untrusted-message")
            .forEach { assertFalse("exception message was retained", output.contains(it)) }
        val record = LogBuffer.snapshot().single()
        assertEquals(LogLevel.ERROR, record.level)
        assertEquals("stream failure", record.message)
        assertTrue("exception type should remain diagnosable", output.contains("IOException"))
        assertTrue("origin frame should remain diagnosable", output.contains("AppLogThrowableSecurityTest"))
    }

    @Test fun `debug error without throwable still records its original sanitized message`() {
        AppLog.e("AppLogNoThrowableTest") { "plain error" }
        val item = ShadowLog.getLogsForTag("AppLogNoThrowableTest").single()
        assertEquals("plain error", item.msg)
        assertNull(item.throwable)
        assertEquals("plain error", LogBuffer.snapshot().single().message)
    }

    @Test fun `debug log sanitizes frame metadata without losing the call site`() {
        val secret = "synthetic-frame-password"
        val failure = IOException("hidden message").apply {
            stackTrace = arrayOf(
                StackTraceElement("app.zorik.player.Fixture", "readStream",
                    "http://user:$secret@example.test/video", 42),
            )
        }
        AppLog.e("AppLogFrameTest", failure) { "stream failure" }
        val item = ShadowLog.getLogsForTag("AppLogFrameTest").single()
        val output = item.msg + "\n" + (item.throwable?.stackTraceToString() ?: "")
        assertFalse(output.contains(secret))
        assertTrue(output.contains("Fixture.readStream"))
    }
}
