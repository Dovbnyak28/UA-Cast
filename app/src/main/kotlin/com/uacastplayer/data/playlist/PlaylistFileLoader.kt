package com.uacastplayer.data.playlist

import android.content.Context
import android.net.Uri
import com.uacastplayer.playlist.BoundedBytesResult
import com.uacastplayer.playlist.BoundedTextReader
import com.uacastplayer.playlist.CharsetDetector
import com.uacastplayer.playlist.PlaylistLoadResult
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads a playlist selected through the Storage Access Framework, capping the read size. */
class PlaylistFileLoader(private val context: Context) {

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
            throw e
        } catch (e: FileNotFoundException) {
            PlaylistLoadResult.ReadError(e.message)
        } catch (e: IOException) {
            PlaylistLoadResult.ReadError(e.message)
        }
    }
}
