package com.uacastplayer.data.epg

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.data.writeSafely
import com.uacastplayer.log.AppLog
import com.uacastplayer.epg.DecodedEpgSnapshot
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgSnapshotCodec
import com.uacastplayer.epg.EpgSnapshotHeader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "EpgSnapshotStore"

class EpgSnapshotStore(context: Context) {

    private val atomicFile = AtomicFile(File(context.filesDir, "epg_snapshot.bin"))

    /** Writes the *parsed* guide, not the XMLTV document it came from - see [EpgSnapshotCodec] for
     * why that changed and what it cost to keep the document. */
    suspend fun save(sourceFingerprint: String, savedAtEpochMillis: Long, data: EpgData) = withContext(Dispatchers.IO) {
        atomicFile.writeSafely(TAG, "EPG snapshot") { stream ->
            EpgSnapshotCodec.encode(EpgSnapshotHeader(sourceFingerprint, savedAtEpochMillis), data, stream)
        }
        Unit
    }

    /**
     * Opens the cached snapshot, or null if there is none or what is on disk does not decode.
     *
     * A [DecodedEpgSnapshot.Document] result (a v1 snapshot written by an older version) stays
     * attached to the underlying stream for its payload, so **the caller must close it**; a
     * [DecodedEpgSnapshot.Parsed] result has already consumed everything it needs and this closes
     * the stream itself.
     */
    suspend fun open(): DecodedEpgSnapshot? = withContext(Dispatchers.IO) {
        val stream = try {
            atomicFile.openRead()
        } catch (_: FileNotFoundException) {
            return@withContext null
        } catch (e: IOException) {
            // Cache only - a null here means "parse from the network instead", which is exactly
            // what an unreadable snapshot calls for. Escaping would take the EPG load down with it.
            AppLog.w(TAG) { "EPG snapshot read failed, will refetch: ${e.javaClass.simpleName}" }
            return@withContext null
        }
        val decoded = EpgSnapshotCodec.decode(stream)
        if (decoded == null || decoded is DecodedEpgSnapshot.Parsed) {
            stream.close()
        }
        decoded
    }
}
