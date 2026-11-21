package com.uacastplayer.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import androidx.core.util.AtomicFile
import com.uacastplayer.backup.BackupCodec
import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupPlaylistSource
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.core.io.BoundedByteReader
import com.uacastplayer.core.io.BoundedBytesResult
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.data.writeSafely
import com.uacastplayer.playlist.PlaylistSource
import java.io.File
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

/** IO-only adapter. No imported path/name is ever used as a writable filesystem path. */
class BackupPlaylistFiles(private val context: Context) {
    private val directory = File(context.filesDir, DIRECTORY)

    fun capture(data: BackupData, cancellationJob: Job): BackupData? = runCatchingNonFatal {
        var remaining = BackupCodec.MAX_BACKUP_BYTES
        val sources = data.sources.map { source ->
            if (source.type != "FILE") return@map source.copy(playlistBase64 = null, playlistDigest = null)
            cancellationJob.ensureActive()
            val bytes = readSource(source.location, remaining / BASE64_GROUP_SIZE * RAW_GROUP_SIZE, cancellationJob)
                ?: return null
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            remaining -= encoded.length
            source.copy(playlistBase64 = encoded, playlistDigest = Fingerprint.of(encoded))
        }
        data.copy(sources = sources)
    }.getOrNull()

    /** Validates all payloads before any source/favorite/settings mutation or disk writes. */
    fun prepare(data: BackupData): BackupData? = runCatchingNonFatal {
        var remaining = BackupCodec.MAX_BACKUP_BYTES
        val sources = data.sources.map { source ->
            val encoded = source.playlistBase64 ?: return@map source // Legacy version 1.
            remaining -= encoded.length
            if (source.type != "FILE" || remaining < 0 || decode(source) == null) return null
            source.copy(location = Uri.fromFile(destination(source)).toString())
        }
        data.copy(sources = sources)
    }.getOrNull()

    /** Only write files admitted by the source merge, not thousands of rows rejected by its limit. */
    fun materialize(data: BackupData, selected: List<PlaylistSource>, cancellationJob: Job): Boolean {
        val retained = selected.mapTo(mutableSetOf()) { it.location }
        val embedded = data.sources.filter { it.playlistBase64 != null && it.location in retained }
        if (embedded.isEmpty()) return true
        return runCatchingNonFatal {
            (directory.isDirectory || directory.mkdirs()) && embedded.all { source ->
                writeSource(source, cancellationJob)
            }
        }.getOrDefault(false)
    }

    private fun writeSource(source: BackupPlaylistSource, cancellationJob: Job): Boolean {
        cancellationJob.ensureActive()
        return decode(source)?.let { bytes ->
            ownedFile(source.location)?.let { file ->
                AtomicFile(file).writeSafely(TAG, "Restored playlist") { it.write(bytes) }
            }
        } ?: false
    }

    private fun destination(source: BackupPlaylistSource): File =
        File(directory, "${Fingerprint.of(source.playlistDigest.orEmpty() + source.addedAtEpochMillis)}.m3u")

    private fun ownedFile(location: String): File? {
        val uri = location.toUri()
        val path = uri.path?.takeIf { uri.scheme == "file" } ?: return null
        val file = File(path).canonicalFile
        return file.takeIf { it.parentFile == directory.canonicalFile && FILE_NAME.matches(it.name) }
    }

    private fun decode(source: BackupPlaylistSource): ByteArray? {
        val encoded = source.playlistBase64 ?: return null
        return if (encoded.length > BackupCodec.MAX_BACKUP_BYTES || Fingerprint.of(encoded) != source.playlistDigest) {
            null
        } else {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            bytes.takeIf { it.isNotEmpty() && Base64.encodeToString(it, Base64.NO_WRAP) == encoded }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun readSource(location: String, limit: Int, cancellationJob: Job): ByteArray? {
        val uri = location.toUri()
        val readable = uri.scheme == "content" || ownedFile(location) != null
        val stream = if (readable) context.contentResolver.openInputStream(uri) else null
        if (stream == null) return null
        val closeOnCancellation = cancellationJob.invokeOnCompletion(onCancelling = true) { cause ->
            if (cause != null) runCatchingNonFatal { stream.close() }
        }
        return try {
            stream.use {
                val bounded = BoundedByteReader.readBytes(it, limit) as? BoundedBytesResult.Success
                bounded?.bytes?.takeIf(ByteArray::isNotEmpty)
            }
        } finally {
            closeOnCancellation.dispose()
        }
    }

    private companion object {
        const val TAG = "BackupPlaylistFiles"
        const val DIRECTORY = "backup_playlists"
        const val BASE64_GROUP_SIZE = 4
        const val RAW_GROUP_SIZE = 3
        val FILE_NAME = Regex("[a-f0-9]{64}\\.m3u")
    }
}
