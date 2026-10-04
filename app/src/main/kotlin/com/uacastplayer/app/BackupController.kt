package com.uacastplayer.app

import android.app.Application
import android.net.Uri
import com.uacastplayer.backup.BackupCodec
import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupExportResult
import com.uacastplayer.backup.BackupImportSummary
import com.uacastplayer.backup.BackupMergePolicy
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.core.security.BackupCipher
import com.uacastplayer.data.favorites.FavoritesRepository
import com.uacastplayer.data.backup.BackupPlaylistFiles
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.BoundedReadResult
import com.uacastplayer.playlist.BoundedTextReader
import com.uacastplayer.playlist.PlaylistSource
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "BackupController"

/**
 * Owns document export/import, portable local playlist files, validation and durability feedback.
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
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
    private val awaitSourcesLoaded: suspend () -> Boolean = { true },
) {
    /** One-shot outcome of [importFrom], displayed and dismissed by the Settings screen. */
    private val _backupImportSummary = MutableStateFlow<BackupImportSummary?>(null)
    val backupImportSummary: StateFlow<BackupImportSummary?> = _backupImportSummary.asStateFlow()

    private val _backupExportResult = MutableStateFlow<BackupExportResult?>(null)
    val backupExportResult: StateFlow<BackupExportResult?> = _backupExportResult.asStateFlow()
    private val exportBusy = MutableStateFlow(false)
    val exportInProgress: StateFlow<Boolean> = exportBusy.asStateFlow()
    private val exportGeneration = AtomicLong()
    private val importGeneration = AtomicLong()
    private val playlistFiles = BackupPlaylistFiles(application)
    private val restoreFilesMutex = Mutex()

    /** Writes [data] as JSON to a SAF-picked [uri] - see [BackupCodec]. [data] deliberately
     * excludes caches/snapshots - those are re-derivable from the sources themselves and would
     * just bloat the file. */
    fun exportTo(uri: Uri, data: BackupData) = launchExport(uri) { data }

    /** Capture live sources and favorites only after both startup reads have completed. */
    fun exportCurrentTo(uri: Uri, password: CharArray? = null, dataSupplier: () -> BackupData) =
        launchExport(uri, password) {
        if (awaitSourcesLoaded()) {
            favoritesRepository.awaitLoaded()
            dataSupplier()
        } else {
            null
        }
    }

    private fun launchExport(uri: Uri, password: CharArray? = null, dataSupplier: suspend () -> BackupData?) {
        val generation = exportGeneration.incrementAndGet()
        _backupExportResult.value = null
        exportBusy.value = true
        val ownedPassword = password?.copyOf()
        val work = scope.launch {
            val data = dataSupplier()
            if (generation != exportGeneration.get()) return@launch
            val encoded = data?.let {
                withContext(ioDispatcher) { encodePortableBackup(it, currentCoroutineContext().job) }
            }
            val bytes = if (ownedPassword != null && encoded != null) {
                withContext(AppDispatchers.cpu) {
                    val owner = currentCoroutineContext().job
                    try {
                        runCatchingNonFatal {
                            BackupCipher.encrypt(encoded, ownedPassword, owner::ensureActive)
                        }.getOrNull()
                    }
                    finally { encoded.fill(0) }
                }
            } else encoded
            val result = bytes != null && withContext(ioDispatcher) {
                exportWriteCoordinator.withDestination(uri.toString()) {
                    // A newer picker result may arrive while this request waits for the same
                    // document. Never let an obsolete waiter open a truncating output handle.
                    generation == exportGeneration.get() && writeEncoded(uri, bytes, currentCoroutineContext().job)
                }
            }
            val outcome = if (result) {
                BackupExportResult.SUCCESS
            } else {
                BackupExportResult.FAILURE
            }
            // The document picker can be opened again while a slow cloud provider is still
            // writing the previous file. Only the newest request may own the one-shot UI result.
            if (generation == exportGeneration.get()) _backupExportResult.value = outcome
        }
        work.invokeOnCompletion {
            ownedPassword?.fill('\u0000')
            if (generation == exportGeneration.get()) exportBusy.value = false
        }
    }

    /**
     * The synchronous write half of [exportTo], split out so provider edge cases can be tested
     * without observing a fire-and-forget coroutine. `openOutputStream` is documented as nullable
     * when its provider has crashed; that used to be treated as a successful export. Expected I/O
     * and expired-grant failures become a visible failure result, while coroutine cancellation and
     * fatal VM errors are deliberately not caught.
     */
    @OptIn(InternalCoroutinesApi::class)
    internal fun writeBackup(uri: Uri, data: BackupData, cancellationJob: Job? = null): Boolean {
        val json = encodePortableBackup(data, cancellationJob ?: Job()) ?: return false
        return writeEncoded(uri, json, cancellationJob)
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun writeEncoded(uri: Uri, json: ByteArray, cancellationJob: Job?): Boolean {
        return runCatchingNonFatal {
            cancellationJob?.ensureActive()
            // "w" alone does not promise truncation on every document provider. A shorter
            // replacement must not retain the previous JSON's tail and become unreadable.
            val output = application.contentResolver.openOutputStream(uri, "wt")
            if (output == null) {
                AppLog.w(TAG) { "Backup export failed: provider returned no output stream" }
                false
            } else {
                val closeOnCancellation = cancellationJob?.invokeOnCompletion(onCancelling = true) { cause ->
                    if (cause != null) runCatchingNonFatal { output.close() }
                }
                try {
                    output.use { stream -> stream.write(json) }
                    true
                } finally {
                    closeOnCancellation?.dispose()
                }
            }
        }.onFailure { e ->
            AppLog.w(TAG) { "Backup export failed: ${e.javaClass.simpleName}" }
        }.getOrDefault(false)
    }

    private fun encodePortableBackup(data: BackupData, cancellationJob: Job): ByteArray? {
        cancellationJob.ensureActive()
        val portable = playlistFiles.capture(data, cancellationJob) ?: return null
        cancellationJob.ensureActive()
        val bytes = BackupCodec.encode(portable).toByteArray(Charsets.UTF_8)
        cancellationJob.ensureActive()
        return bytes.takeIf { it.size <= MAX_BACKUP_BYTES }
    }

    /** Reads a SAF-picked [uri] and merges its sources/favorites into the latest values supplied by
     * [currentSources]/[currentFavorites] (see [BackupMergePolicy]), then hands the result back
     * through the two callbacks. Invalid/unreadable files produce a visible rejection without
     * changing any existing sources, favorites or settings. */
    fun importFrom(
        uri: Uri,
        currentSources: () -> List<PlaylistSource>,
        currentFavorites: () -> List<FavoriteChannel>,
        onSourcesMerged: suspend (List<PlaylistSource>) -> Unit,
        onSettingsImported: (BackupSettings) -> Unit,
        awaitSourcesPersisted: suspend () -> Boolean = { true },
    ): Job {
        val generation = importGeneration.incrementAndGet()
        _backupImportSummary.value = null
        return scope.launch {
            // Read and parse off the main thread. Parsing used to run where scope.launch left it,
            // and viewModelScope is Dispatchers.Main.immediate, so a large backup blocked frames.
            val data = readPortableBackup(uri)
            if (data == null) {
                rejectFile(generation)
                return@launch
            }
            applyDecodedData(
                generation, data,
                BackupRestoreTarget(
                    currentSources, currentFavorites, onSourcesMerged, onSettingsImported, awaitSourcesPersisted,
                ),
            )
        }
    }

    internal fun importDecoded(data: BackupData, target: BackupRestoreTarget): Job {
        val generation = importGeneration.incrementAndGet()
        _backupImportSummary.value = null
        return scope.launch { applyDecodedData(generation, data, target) }
    }

    private suspend fun applyDecodedData(generation: Long, data: BackupData, target: BackupRestoreTarget) = run {
            // Waiting only at the persistence stage is too late: reorder() treats a merge as
            // a replacement, so an initial read still in flight would lose its existing rows.
            favoritesRepository.awaitLoaded()
            if (!awaitSourcesLoaded()) {
                if (generation == importGeneration.get()) {
                    _backupImportSummary.value = BackupImportSummary(0, 0, persistenceFailed = true)
                }
                return@run
            }
            val mergeResult = mergeWithLatestState(generation, data, target.currentSources, target.currentFavorites)
                ?: return@run
            if (generation != importGeneration.get()) return@run

            target.onSourcesMerged(mergeResult.sources)
            if (generation != importGeneration.get()) return@run
            // "reorder" also just means "replace wholesale + persist" - there's no dedicated
            // bulk-set method on FavoritesRepository, and this does exactly what's needed here.
            val favorites = mergeFavoritesAfterSourceSave(generation, data, mergeResult, target.currentFavorites)
                ?: return@run
            favoritesRepository.reorder(favorites.favorites)
            target.onSettingsImported(data.settings)

            val sourcesSaved = target.awaitSourcesPersisted()
            val favoritesSaved = favoritesRepository.awaitPersistence()
            if (generation != importGeneration.get()) return@run

            _backupImportSummary.value = BackupImportSummary(
                mergeResult.importedSourceCount,
                favorites.importedFavoriteCount,
                persistenceFailed = !sourcesSaved || !favoritesSaved,
                sourceLimitExceededCount = mergeResult.sourceLimitExceededCount,
            )
    }

    internal fun preparePortableData(data: BackupData): BackupData? = playlistFiles.prepare(data)

    private suspend fun readPortableBackup(uri: Uri): BackupData? = withContext(ioDispatcher) {
        val text = readBoundedText(uri, currentCoroutineContext().job) ?: return@withContext null
        val data = BackupCodec.decode(text) ?: return@withContext null
        playlistFiles.prepare(data)
    }

    private fun rejectFile(generation: Long) {
        if (generation == importGeneration.get()) {
            _backupImportSummary.value = BackupImportSummary(0, 0, fileRejected = true)
        }
    }

    /**
     * Computes away from the owner dispatcher, then validates the snapshots after returning to it.
     * AppViewModel's scope serializes the final equality check and the synchronous callbacks below,
     * so a favorite/source changed while a slow provider was read or a large merge was calculated
     * is retained rather than being overwritten by an obsolete base list.
     */
    private suspend fun mergeWithLatestState(
        generation: Long,
        data: BackupData,
        currentSources: () -> List<PlaylistSource>,
        currentFavorites: () -> List<FavoriteChannel>,
    ): BackupMergePolicy.MergeResult? {
        while (generation == importGeneration.get()) {
            val sources = currentSources()
            val favorites = currentFavorites()
            val merged = withContext(ioDispatcher) {
                val result = BackupMergePolicy.merge(
                    existingSources = sources,
                    existingFavorites = favorites,
                    importedSources = data.sources,
                    importedFavorites = data.favorites,
                )
                val saved = restoreFilesMutex.withLock {
                    playlistFiles.materialize(data, result.sources, currentCoroutineContext().job)
                }
                result.takeIf { saved }
            }
            if (merged == null) {
                rejectFile(generation)
                break
            }
            if (sources == currentSources() && favorites == currentFavorites()) return merged
        }
        return null
    }

    private suspend fun mergeFavoritesAfterSourceSave(
        generation: Long,
        data: BackupData,
        initial: BackupMergePolicy.MergeResult,
        currentFavorites: () -> List<FavoriteChannel>,
    ): BackupMergePolicy.MergeResult? {
        while (generation == importGeneration.get()) {
            val favorites = currentFavorites()
            // No extra IO hop if source persistence did not overlap a user favorite edit.
            val originalCount = initial.favorites.size - initial.importedFavoriteCount
            val unchanged = originalCount == favorites.size && initial.favorites.subList(0, originalCount) == favorites
            val result = if (unchanged) {
                initial
            } else {
                withContext(ioDispatcher) {
                    BackupMergePolicy.merge(emptyList(), favorites, emptyList(), data.favorites)
                }
            }
            if (favorites == currentFavorites()) return result
        }
        return null
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
     * A content provider is somebody else's code, so runtime failures at that boundary remain a
     * failed import. Coroutine cancellation and fatal VM errors are different: neither is input
     * damage, and both must escape instead of being disguised as an unreadable file.
     */
    @OptIn(InternalCoroutinesApi::class)
    internal fun readBoundedText(uri: Uri, cancellationJob: Job? = null): String? =
        runCatchingNonFatal {
            application.contentResolver.openInputStream(uri)?.use { stream ->
                val closeOnCancellation = cancellationJob?.invokeOnCompletion(onCancelling = true) { cause ->
                    if (cause != null) runCatchingNonFatal { stream.close() }
                }
                try {
                    when (val bounded = BoundedTextReader.readText(stream, MAX_BACKUP_BYTES)) {
                        is BoundedReadResult.Success -> bounded.text
                        BoundedReadResult.SizeLimitExceeded -> {
                            AppLog.w(TAG) { "Backup import refused: larger than $MAX_BACKUP_BYTES bytes" }
                            null
                        }
                    }
                } finally {
                    closeOnCancellation?.dispose()
                }
            }
        }
            .onFailure { e -> AppLog.w(TAG) { "Backup import read failed: ${e.javaClass.simpleName}" } }
            .getOrNull()

    fun dismissImportSummary() {
        _backupImportSummary.value = null
    }

    fun dismissExportResult() {
        _backupExportResult.value = null
    }

    companion object {
        // Separate Activity/ViewModel owners can still select the same provider document.
        // A process-wide coordinator holds only URI keys for active/waiting exports, not owners.
        private val exportWriteCoordinator = BackupExportCoordinator()

        /**
         * Bounds the complete JSON, including embedded local playlists, favorites and settings.
         * Base64 expansion counts towards this cap; oversized backups fail before opening the
         * output document, never silently omitting a local playlist to make the file fit.
         */
        const val MAX_BACKUP_BYTES = BackupCodec.MAX_BACKUP_BYTES
    }
}
