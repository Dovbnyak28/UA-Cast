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
import com.uacastplayer.playlist.PlaylistSource
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
) {
    /** One-shot result of the last successful [importFrom] - the Settings screen shows it (e.g.
     * as a toast) and clears it via [dismissImportSummary]. */
    private val _backupImportSummary = MutableStateFlow<BackupImportSummary?>(null)
    val backupImportSummary: StateFlow<BackupImportSummary?> = _backupImportSummary.asStateFlow()

    /** Writes [data] as JSON to a SAF-picked [uri] - see [BackupCodec]. [data] deliberately
     * excludes caches/snapshots - those are re-derivable from the sources themselves and would
     * just bloat the file. */
    fun exportTo(uri: Uri, data: BackupData) {
        scope.launch(Dispatchers.IO) {
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
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    application.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }
                    .onFailure { e -> AppLog.w(TAG) { "Backup import read failed: ${e.javaClass.simpleName}" } }
                    .getOrNull()
            } ?: return@launch
            val data = BackupCodec.decode(text) ?: return@launch

            val mergeResult = BackupMergePolicy.merge(
                existingSources = currentSources,
                existingFavorites = currentFavorites,
                importedSources = data.sources,
                importedFavorites = data.favorites,
            )
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

    fun dismissImportSummary() {
        _backupImportSummary.value = null
    }
}
