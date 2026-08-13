package com.uacastplayer.app

import android.app.Application
import android.net.Uri
import com.uacastplayer.backup.BackupCodec
import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupImportSummary
import com.uacastplayer.backup.BackupMergePolicy
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.data.favorites.FavoritesRepository
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.BoundedReadResult
import com.uacastplayer.playlist.BoundedTextReader
import com.uacastplayer.playlist.PlaylistSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "BackupController"

/**
 * Owns backup export/import - moved out of [com.uacastplayer.AppViewModel] as a move-only split
 * (see B1 in the consolidated fix plan); behavior is unchanged, this is still thin impure glue.
 *
 * Doesn't own playlist sources or settings itself, so a successful import hands the merged sources
 * and imported settings back to the caller via [onSourcesMerged]/[onSettingsImported] rather than
 * mutating them directly - those callbacks are AppViewModel's existing setters, kept in sync one
 * place instead of duplicated here.
 */
class BackupController(
    private val application: Application,
    private val favoritesRepository: FavoritesRepository,
    private val scope: CoroutineScope,
    /** Where the file is read and parsed. Injected rather than hardcoded for the same reason
     * [ParentalControlController]'s hashing dispatcher is: tests stay on their own dispatcher
     * instead of hopping to a real thread pool mid-assertion. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** One-shot result of the last successful [importFrom] - the Settings screen shows it (e.g.
     * as a toast) and clears it via [dismissImportSummary]. */
    private val _backupImportSummary = MutableStateFlow<BackupImportSummary?>(null)
    val backupImportSummary: StateFlow<BackupImportSummary?> = _backupImportSummary.asStateFlow()

    /** Writes [data] as JSON to a SAF-picked [uri] - see [BackupCodec]. [data] deliberately
     * excludes caches/snapshots - those are re-derivable from the sources themselves and would
     * just bloat the file. */
    fun exportTo(uri: Uri, data: BackupData) {
        scope.launch(ioDispatcher) {
            val json = BackupCodec.encode(data)
            runCatching {
                application.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
            }.onFailure { e -> AppLog.w(TAG) { "Backup export failed: ${e.javaClass.simpleName}" } }
        }
    }

    /** Reads a SAF-picked [uri] and merges its sources/favorites into [currentSources]/
     * [currentFavorites] (see [BackupMergePolicy]), then hands the result back through the two
     * callbacks. A no-op (nothing happens, no summary shown) for an unreadable file, an empty one,
     * or one with an unrecognized [BackupCodec] version. */
    fun importFrom(
        uri: Uri,
        currentSources: List<PlaylistSource>,
        currentFavorites: List<FavoriteChannel>,
        onSourcesMerged: (List<PlaylistSource>) -> Unit,
        onSettingsImported: (BackupSettings) -> Unit,
    ) {
        scope.launch {
            // Read, parse and merge together and off the main thread. Parsing was the part that
            // was not: decode() ran where scope.launch left it, and viewModelScope is
            // Dispatchers.Main.immediate, so a large backup was parsed on the frame-drawing
            // thread. The merge follows it here rather than staying behind for a second hop it
            // would not earn.
            val imported = withContext(ioDispatcher) {
                val text = readBoundedText(uri) ?: return@withContext null
                val data = BackupCodec.decode(text) ?: return@withContext null
                data to BackupMergePolicy.merge(
                    existingSources = currentSources,
                    existingFavorites = currentFavorites,
                    importedSources = data.sources,
                    importedFavorites = data.favorites,
                )
            } ?: return@launch
            val (data, mergeResult) = imported

            onSourcesMerged(mergeResult.sources)
            // "reorder" also just means "replace wholesale + persist" - there's no dedicated
            // bulk-set method on FavoritesRepository, and this does exactly what's needed here.
            favoritesRepository.reorder(mergeResult.favorites)
            onSettingsImported(data.settings)

            _backupImportSummary.value = BackupImportSummary(
                mergeResult.importedSourceCount,
                mergeResult.importedFavoriteCount,
            )
        }
    }

    /**
     * The picked file's text, or null if it could not be read or is too big to be a backup.
     *
     * The cap is the point. Every other stream this app reads is bounded - the proxy's playlists,
     * the EPG download, an icon, and in particular [com.uacastplayer.data.playlist.PlaylistFileLoader],
     * the other file the user picks through SAF, which has used [BoundedTextReader] since it was
     * written. This one called `readText()` and took whatever came, so picking the wrong file - a
     * video, a disk image, anything at all - pulled it into memory as a String on a phone whose own
     * diagnostics report a 256MB heap.
     *
     * `runCatching` stays as broad as it was: a content provider is somebody else's code, it is
     * under no obligation to fail in a way this app anticipated, and `SecurityException` from an
     * expired SAF grant is the ordinary case rather than the exotic one (see PlaylistFileLoaderTest).
     */
    private fun readBoundedText(uri: Uri): String? = runCatching {
        application.contentResolver.openInputStream(uri)?.use { stream ->
            when (val bounded = BoundedTextReader.readText(stream, MAX_BACKUP_BYTES)) {
                is BoundedReadResult.Success -> bounded.text
                BoundedReadResult.SizeLimitExceeded -> {
                    AppLog.w(TAG) { "Backup import refused: larger than $MAX_BACKUP_BYTES bytes" }
                    null
                }
            }
        }
    }
        .onFailure { e -> AppLog.w(TAG) { "Backup import read failed: ${e.javaClass.simpleName}" } }
        .getOrNull()

    fun dismissImportSummary() {
        _backupImportSummary.value = null
    }

    companion object {
        /**
         * A backup holds at most [com.uacastplayer.playlist.PlaylistSourcePolicy.MAX_SOURCES]
         * sources, the favorites, and five settings strings - nothing that grows with the playlist
         * itself. A favorite serialises to roughly 200 bytes at this file's indent, so this is room
         * for something like forty thousand of them: far past any real list, and far short of a file
         * that could not be held in memory.
         */
        const val MAX_BACKUP_BYTES = 8 * 1024 * 1024
    }
}
