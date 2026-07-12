package com.uacastplayer.data.epg

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.epg.EpgSnapshot
import com.uacastplayer.epg.EpgSnapshotCodec
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EpgSnapshotStore(context: Context) {

    private val atomicFile = AtomicFile(File(context.filesDir, "epg_snapshot.bin"))

    suspend fun save(snapshot: EpgSnapshot) = withContext(Dispatchers.IO) {
        val stream = atomicFile.startWrite()
        try {
            EpgSnapshotCodec.encode(snapshot, stream)
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            throw e
        }
    }

    suspend fun load(): EpgSnapshot? = withContext(Dispatchers.IO) {
        try {
            atomicFile.openRead().use { EpgSnapshotCodec.decode(it) }
        } catch (_: FileNotFoundException) {
            null
        }
    }
}
