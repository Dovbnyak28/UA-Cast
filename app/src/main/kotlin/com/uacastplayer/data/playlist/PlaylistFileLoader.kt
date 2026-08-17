package com.uacastplayer.data.playlist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.BoundedBytesResult
import com.uacastplayer.playlist.BoundedTextReader
import com.uacastplayer.playlist.CharsetDetector
import com.uacastplayer.playlist.PlaylistLoadResult
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PlaylistFileLoader"

/** Reads a playlist selected through the Storage Access Framework, capping the read size. */
class PlaylistFileLoader(private val context: Context) {

    /**
     * Asks to keep reading [uri] after this process ends.
     *
     * The grant `ACTION_OPEN_DOCUMENT` hands back dies with the task, and a saved file playlist
     * outlives the task by design - its URI is written into the sources list and reloaded on a
     * later launch. Without this the reload could only ever fail, and the user's only recourse was
     * to pick the same file again, with nothing on screen explaining why.
     *
     * Failure is not worth reporting: a provider may refuse a persistable grant, and some URIs -
     * anything that did not come from `ACTION_OPEN_DOCUMENT` - cannot take one at all. Both leave
     * the app exactly where it was before, reading from a grant that lasts as long as the task.
     */
    fun rememberAccess(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { e ->
            AppLog.w(TAG) { "No persistable access to the picked file: ${e.javaClass.simpleName}" }
        }
    }

    /**
     * The file name the document provider knows [uri] by, or null if it will not say.
     *
     * A Storage Access Framework URI carries nothing readable - the user's own playlist is
     * `content://com.android.providers.downloads.documents/document/msf%3A965`, where the last
     * segment is a row id in someone else's database. The name lives behind the provider, and this
     * is the only way to ask for it: `playlist.m3u8` instead of a hash on the home screen.
     *
     * Asked once, when the playlist is added, and stored - not looked up on every render. The
     * answer needs the read grant, and a saved playlist outlives grants (see [rememberAccess]).
     */
    fun documentName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Reads [uri], or says why it could not.
     *
     * **Every failure is caught, including the ones that are not [IOException].** What sits on the
     * other side of `ContentResolver` is a document provider written by someone else - Google
     * Drive, Dropbox, an OEM file manager - and it can fail in any way it likes: a
     * `SecurityException` when the grant has lapsed (which is the ordinary case for a saved file
     * playlist reloaded in a new process), an `IllegalArgumentException` for an authority that no
     * longer exists because the provider app was uninstalled, or something entirely of its own
     * invention. This method runs inside a `viewModelScope` coroutine with no exception handler
     * above it, so anything that escapes is not an error message on screen - it is the app closing.
     * Naming the three exception types that were known about at the time is how that crash got
     * shipped once already.
     */
    @Suppress("TooGenericExceptionCaught")

    suspend fun load(uri: Uri): PlaylistLoadResult = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext PlaylistLoadResult.ReadError("Unable to open file")
            stream.use {
                // A local file (picked via the Storage Access Framework) has no Content-Type to
                // consult - always sniff the bytes.
                when (val bounded = BoundedTextReader.readBytes(it, PlaylistUrlLoader.MAX_PLAYLIST_BYTES)) {
                    is BoundedBytesResult.Success -> PlaylistLoadResult.Success(CharsetDetector.decode(bounded.bytes))
                    BoundedBytesResult.SizeLimitExceeded -> PlaylistLoadResult.SizeLimitExceeded
                }
            }
        } catch (e: CancellationException) {
            // The read suspends, so a cancelled scope must not be reported as a failed file.
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG) { "Cannot read the picked playlist: ${e.javaClass.simpleName}" }
            // The same class name the log line above already uses, not e.message - see
            // PlaylistLoadResult.ReadError's own doc for why.
            PlaylistLoadResult.ReadError(e.javaClass.simpleName)
        }
    }
}
