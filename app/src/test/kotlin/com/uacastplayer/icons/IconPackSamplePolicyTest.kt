package com.uacastplayer.icons

import com.uacastplayer.playlist.M3uChannel
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IconPackSamplePolicyTest {
    @Test fun selectsSixUniqueIdsAndCountsMissingAcrossWholePlaylist() {
        val channels = List(100_000) {
            M3uChannel("Channel $it", "https://test/$it", tvgId = if (it % 2 == 0) "$it" else null)
        }
        val plan = IconPackSamplePolicy.select(channels)
        assertEquals(6, plan.channels.size)
        assertEquals(50_000, plan.missingIds)
        assertEquals(100_000, plan.total)
    }

    @Test fun repeatedIdsNeverCreateRepeatedRequests() {
        val channel = M3uChannel("One", "https://test/one", tvgId = "same")
        assertEquals(listOf(channel), IconPackSamplePolicy.select(List(20) { channel }).channels)
    }

    @Test fun cancelledSelectionStopsBeforeCreatingRequests() {
        assertThrows(CancellationException::class.java) {
            IconPackSamplePolicy.select(List(1000) { M3uChannel("Channel", "https://test/$it") }) {
                throw CancellationException()
            }
        }
    }
}
