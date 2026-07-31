package com.uacastplayer.data.epg

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.epg.EpgSnapshotCodec
import com.uacastplayer.epg.EpgSnapshotHeader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EpgSnapshotStore(context: Context) {

    private val atomicFile = AtomicFile(File(context.filesDir, "epg_snapshot.bin"))

    /** Streams [documentFile]'s contents straight into the snapshot file - the document is never
     * fully materialized as a ByteArray, since EPG feeds can run tens of megabytes. */
    // AtomicFile requires failWrite() on *any* failure mid-write to release the temp file, not just
    // specific ones - the broad catch exists to make that cleanup unconditional before rethrowing.
    @Suppress("TooGenericExceptionCaught")
    suspend fun save(sourceFingerprint: String, savedAtEpochMillis: Long, documentFile: File) = withContext(Dispatchers.IO) {
        val stream = atomicFile.startWrite()
        try {
            documentFile.inputStream().use { documentStream ->
                EpgSnapshotCodec.encode(
                    EpgSnapshotHeader(sourceFingerprint, savedAtEpochMillis),
                    documentStream,
                    documentFile.length(),
                    stream,
                )
            }
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            throw e
        }
    }

    /**
     * Returns the still-open, header-skipped read stream for the cached document, positioned so
     * the caller can parse straight from it - or null if there's no cached snapshot, or what's on
     * disk doesn't decode. Callers must close the returned stream.
     */
    suspend fun openDocumentStream(): InputStream? = withContext(Dispatchers.IO) {
        val stream = try {
            atomicFile.openRead()
        } catch (_: FileNotFoundException) {
            return@withContext null
        }
        val decoded = EpgSnapshotCodec.decodeHeader(stream)
        if (decoded == null) {
            stream.close()
            return@withContext null
        }
        decoded.documentStream
    }
}
