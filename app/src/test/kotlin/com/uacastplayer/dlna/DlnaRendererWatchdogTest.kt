package com.uacastplayer.dlna

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DlnaRendererWatchdogTest {
    @Test fun `three consecutive unreachable observations end the session exactly once`() = runTest {
        var ended = 0
        val watchdog = DlnaRendererWatchdog(backgroundScope) { DlnaTransportHealth.UNREACHABLE }
        watchdog.start("renderer") { ended++ }
        advanceTimeBy(59_999)
        assertEquals(0, ended)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, ended)
        advanceTimeBy(120_000)
        assertEquals(1, ended)
    }

    @Test fun `healthy observation resets failures and stop cancels later checks`() = runTest {
        var ended = 0
        var checks = 0
        val watchdog = DlnaRendererWatchdog(backgroundScope) {
            checks++
            if (checks == 3) DlnaTransportHealth.ACTIVE else DlnaTransportHealth.INACTIVE
        }
        watchdog.start("renderer") { ended++ }
        advanceTimeBy(100_001)
        assertEquals(0, ended)
        watchdog.stop()
        advanceTimeBy(120_000)
        assertEquals(5, checks)
        assertEquals(0, ended)
    }

    @Test fun `unsupported legacy status query disables polling rather than disconnecting`() = runTest {
        var checks = 0
        val watchdog = DlnaRendererWatchdog(backgroundScope) { checks++; DlnaTransportHealth.UNSUPPORTED }
        watchdog.start("renderer") { error("legacy renderer disconnected") }
        advanceTimeBy(120_000)
        assertEquals(1, checks)
    }

    @Test fun `bounded status parser recognizes namespaced playback and old renderer faults`() {
        assertEquals(DlnaTransportHealth.ACTIVE, DlnaTransportStateReader.parse(
            200, "<u:CurrentTransportState>PAUSED_PLAYBACK</u:CurrentTransportState>",
        ))
        assertEquals(DlnaTransportHealth.INACTIVE, DlnaTransportStateReader.parse(
            200, "<CurrentTransportState>STOPPED</CurrentTransportState>",
        ))
        assertEquals(DlnaTransportHealth.UNSUPPORTED, DlnaTransportStateReader.parse(
            500, "<errorCode>401</errorCode>",
        ))
        assertEquals(DlnaTransportHealth.UNREACHABLE, DlnaTransportStateReader.parse(500, "broken"))
        assertEquals(DlnaTransportHealth.INACTIVE, DlnaTransportStateReader.parse(
            200, "<CurrentTransportState>TRANSITIONING</CurrentTransportState>",
        ))
    }
}
