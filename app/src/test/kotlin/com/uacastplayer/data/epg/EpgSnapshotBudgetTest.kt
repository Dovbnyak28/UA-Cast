package com.uacastplayer.data.epg

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.epg.DecodedEpgSnapshot
import com.uacastplayer.epg.EpgChannel
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgIndex
import com.uacastplayer.epg.EpgProgramme
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpgSnapshotBudgetTest {

    @Test
    fun `oversized snapshot is discarded before decoding`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshotFile = File(context.filesDir, "epg_snapshot.bin")
        val store = EpgSnapshotStore(context, StandardTestDispatcher(testScheduler))
        snapshotFile.delete()

        try {
            RandomAccessFile(snapshotFile, "rw").use { file ->
                file.setLength(EpgSnapshotStore.MAX_SNAPSHOT_BYTES + 1)
            }

            assertNull(store.open())
            assertTrue("an unreadable oversized cache must not be retried at every startup", !snapshotFile.exists())
        } finally {
            snapshotFile.delete()
        }
    }

    @Test
    fun `snapshot store passes the current device budget to the decoder`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshotFile = File(context.filesDir, "epg_snapshot.bin")
        val store = EpgSnapshotStore(
            context = context,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            maxProgrammes = 2,
        )
        snapshotFile.delete()

        try {
            store.save(
                sourceFingerprint = "budget-test",
                savedAtEpochMillis = 1L,
                data = EpgData(
                    index = EpgIndex(listOf(EpgChannel("one", listOf("One"), null))),
                    programmesByChannelId = mapOf(
                        "one" to listOf(
                            EpgProgramme("one", 1L, 2L, "A"),
                            EpgProgramme("one", 3L, 4L, "B"),
                            EpgProgramme("one", 5L, 6L, "C"),
                        ),
                    ),
                ),
            )

            val decoded = store.open() as DecodedEpgSnapshot.Parsed

            assertEquals(2, decoded.data.programmesByChannelId.values.sumOf { it.size })
            assertTrue(decoded.data.truncation.programmesDropped)
        } finally {
            snapshotFile.delete()
        }
    }
}
