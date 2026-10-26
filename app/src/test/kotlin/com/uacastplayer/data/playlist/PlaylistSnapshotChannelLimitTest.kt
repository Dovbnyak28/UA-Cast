package com.uacastplayer.data.playlist

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.playlist.M3uParser
import java.io.DataOutputStream
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistSnapshotChannelLimitTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `old snapshot above parser limit becomes explicit outcome without reading records`() = runTest {
        val sourceId = "oversized-${System.nanoTime()}"
        val snapshotFile = File(context.filesDir, "playlist_snapshot_$sourceId.bin")
        snapshotFile.outputStream().use { stream ->
            DataOutputStream(stream).apply {
                writeInt(2)
                writeUTF("fingerprint")
                writeBoolean(false)
                writeLong(1L)
                writeInt(0)
                writeInt(M3uParser.MAX_CHANNELS + 1)
            }
        }

        try {
            val outcome = PlaylistRepository(context).restoreSnapshot(sourceId)

            assertEquals(PlaylistOutcome.ChannelLimitExceeded, outcome)
        } finally {
            snapshotFile.delete()
        }
    }
}
