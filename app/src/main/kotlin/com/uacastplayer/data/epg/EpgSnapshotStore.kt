package com.uacastplayer.data.epg

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.util.AtomicFile
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.data.writeSafely
import com.uacastplayer.log.AppLog
import com.uacastplayer.epg.DecodedEpgSnapshot
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgSnapshotCodec
import com.uacastplayer.epg.EpgSnapshotHeader
import com.uacastplayer.performance.HeapBudget
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "EpgSnapshotStore"

class EpgSnapshotStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
    private val maxProgrammes: Int = HeapBudget.maxProgrammes(Runtime.getRuntime().maxMemory()),
) {

    private val atomicFile = AtomicFile(File(context.filesDir, "epg_snapshot.bin"))

    /** Writes the *parsed* guide, not the XMLTV document it came from - see [EpgSnapshotCodec] for
     * why that changed and what it cost to keep the document. */
    suspend fun save(sourceFingerprint: String, savedAtEpochMillis: Long, data: EpgData) = withContext(ioDispatcher) {
        atomicFile.writeSafely(TAG, "EPG snapshot") { stream ->
            EpgSnapshotCodec.encode(EpgSnapshotHeader(sourceFingerprint, savedAtEpochMillis), data, stream)
        }
        Unit
    }

    /** Deletes both the committed snapshot and AtomicFile's unfinished side file, if present. */
    suspend fun delete() = withContext(ioDispatcher) {
        atomicFile.delete()
    }

    /**
     * Opens the cached snapshot, or null if there is none or what is on disk does not decode.
     *
     * A [DecodedEpgSnapshot.Document] result (a v1 snapshot written by an older version) stays
     * attached to the underlying stream for its payload, so **the caller must close it**; a
     * [DecodedEpgSnapshot.Parsed] result has already consumed everything it needs and this closes
     * the stream itself.
     */
    suspend fun open(): DecodedEpgSnapshot? = withContext(ioDispatcher) {
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
        if (atomicFile.baseFile.length() > MAX_SNAPSHOT_BYTES) {
            // The parser caps the decompressed source at 64 MiB and the binary snapshot adds
            // bounded record framing. A substantially larger file cannot be one this build
            // writes; decoding it would only spend startup time and allocations on corrupt data.
            runCatchingNonFatal { stream.close() }
            atomicFile.delete()
            AppLog.w(TAG) { "EPG snapshot exceeds the supported size; discarded and will refetch" }
            return@withContext null
        }
        val decoded = EpgSnapshotCodec.decode(stream, maxProgrammes)
        if (decoded == null || decoded is DecodedEpgSnapshot.Parsed) {
            stream.close()
        }
        decoded
    }

    companion object {
        /** Twice the decompressed XML cap, leaving headroom for binary framing and text encoding. */
        @VisibleForTesting
        internal const val MAX_SNAPSHOT_BYTES = 128L * 1024 * 1024
    }
}
