package com.uacastplayer.data.media

import android.content.Context
import android.content.Intent
import android.os.CancellationSignal
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.media.LocalVideo
import com.uacastplayer.media.LocalVideoFormats
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface LocalVideoReader {
    suspend fun read(uri: String, persist: Boolean): LocalVideo
}

/** Metadata/permission work is on IO. Cancellation reaches the provider, not just the UI result. */
class AndroidLocalVideoReader(context: Context) : LocalVideoReader {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(uri: String, persist: Boolean): LocalVideo = withContext(AppDispatchers.io) {
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            runCatchingNonFatal { readMetadata(uri, persist, cancellation) }
                .onSuccess { continuation.resume(it) }
                .onFailure { continuation.resumeWithException(it) }
        }
    }

    private fun readMetadata(value: String, persist: Boolean, signal: CancellationSignal): LocalVideo {
        require(value.length <= LocalVideo.MAX_URI_LENGTH)
        val uri = value.toUri()
        require(uri.scheme.equals("content", true) && !uri.authority.isNullOrBlank())
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, signal)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        }?.filterNot(Char::isISOControl)?.trim()?.take(LocalVideo.MAX_NAME_LENGTH)?.takeIf(String::isNotBlank)
            ?: "Video"
        val mime = requireNotNull(LocalVideoFormats.mimeType(name, resolver.getType(uri))) { "Not a video document" }
        resolver.openAssetFileDescriptor(uri, "r", signal)?.use { asset ->
            require(asset.declaredLength != 0L) { "Empty video document" }
        } ?: throw java.io.FileNotFoundException("Video is unavailable")
        signal.throwIfCanceled()
        val persisted = persist && retainReadPermission(uri)
        return LocalVideo(value, name, mime, persisted)
    }

    private fun retainReadPermission(uri: android.net.Uri): Boolean = try {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: SecurityException) {
        // Some providers supply only a session grant. Playback is still allowed, restoration is not.
        false
    }
}
