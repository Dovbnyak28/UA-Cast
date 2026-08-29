package com.uacastplayer.app

import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.data.icons.IconPrefetcher
import com.uacastplayer.data.icons.IconRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.icons.LogoUpdateReminder
import com.uacastplayer.icons.PrefetchSelectionPolicy
import com.uacastplayer.log.AppLog
import com.uacastplayer.performance.DeviceTier
import com.uacastplayer.player.PlaybackActivity
import com.uacastplayer.playlist.M3uChannel
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

private const val TAG = "IconController"

/**
 * Owns icon resolution, background prefetch, and custom icon sources - moved out of
 * [com.uacastplayer.AppViewModel] as a move-only split (see B1 in the consolidated fix plan);
 * behavior is unchanged, this is still thin impure glue over [IconRepository]/[IconPrefetcher].
 *
 * Icon *display mode* itself (the setting that decides whether prefetch/resolve run at all)
 * lives in [SettingsUiState][com.uacastplayer.settings.SettingsUiState] on AppViewModel, not
 * here - callers pass the current mode in where it's needed instead of this controller reading
 * settings state directly.
 *
 * [onPrefetchFinished] is AppViewModel's hook into the cross-controller cache-size-refresh
 * concern that doesn't belong to icon state itself.
 */
class IconController(
    private val preferences: AppPreferences,
    private val iconRepository: IconRepository,
    private val iconPrefetcher: IconPrefetcher,
    private val scope: CoroutineScope,
    private val onPrefetchFinished: () -> Unit,
    private val maintenanceDispatcher: CoroutineDispatcher = AppDispatchers.io,
) {
    val sources = IconSourceController(iconRepository)

    private val _iconPrefetchState = MutableStateFlow(
        IconPrefetchUiState(
            wifiOnly = preferences.iconWifiOnly,
            updateReminderDue = LogoUpdateReminder.isDue(
                preferences.lastIconPrefetchAtMillis,
                System.currentTimeMillis(),
            ),
        )
    )
    val iconPrefetchState: StateFlow<IconPrefetchUiState> = _iconPrefetchState.asStateFlow()

    private val prefetchLifecycleLock = Any()
    private val activePrefetchJobs = mutableSetOf<Job>()
    private val activeIconResolutions = mutableSetOf<CompletableDeferred<Unit>>()
    private val iconCacheClearWaiters = mutableSetOf<CompletableDeferred<Unit>>()
    private val activeIconCacheClears = mutableSetOf<Long>()
    private var nextIconCacheClearId = 0L
    private var prefetchGeneration = 0L
    private var watcherGeneration = 0L
    private var disposed = false
    private var networkWatcher: AutoCloseable? = null
    private var lastPrefetchChannels: List<M3uChannel> = emptyList()
    private var lastEpgIconUrlFor: (M3uChannel) -> String? = { null }
    private var lastIconDisplayMode: IconDisplayMode = IconDisplayMode.DEFAULT
    private var lastPrefetchContext: PrefetchContext = PrefetchContext()
    private var iconPrefetchJob: Job? = null

    // Cancels an in-flight prefetch the moment playback/casting starts, and re-arms it (from the
    // most recent triggerPrefetch call) once playback stops - see PrefetchSelectionPolicy's doc for
    // why this matters on a large playlist. A restart doesn't repeat network work for channels
    // already fetched before the interruption: IconRepository.resolveIconFile checks its disk cache
    // before ever hitting the network.
    init {
        scope.launch {
            PlaybackActivity.isActive.collect { active ->
                if (active) {
                    // cancelPrefetch, not a bare job cancel: runPrefetch clears isRunning on its
                    // own last line, which a cancellation never reaches, so cancelling the job
                    // alone left the flag set. Both places that read it draw progress - the top-bar
                    // DownloadStatusBanner and the channel list's LinearProgressIndicator - so a
                    // prefetch interrupted by a *cast* (playback the user watches from this very
                    // screen, unlike local playback which covers it) left both frozen at whatever
                    // percentage they had reached, for the whole session.
                    cancelPrefetch()
                } else if (hasPrefetchRequest()) {
                    startPrefetchJob()
                }
            }
        }
    }

    suspend fun resolveChannelIcon(channel: M3uChannel, iconDisplayMode: IconDisplayMode, epgIconUrl: String?): File? {
        if (iconDisplayMode == IconDisplayMode.PLACEHOLDERS) return null
        val cacheLease = acquireIconCacheLease()
        return try {
            iconRepository.resolveIconFile(channel.tvgLogo, epgIconUrl, channel.tvgId)
        } finally {
            releaseIconCacheLease(cacheLease)
        }
    }

    /**
     * The logo URL to hand a Cast receiver for [channel] - see
     * [CastArtworkPolicy][com.uacastplayer.icons.CastArtworkPolicy].
     *
     * Deliberately ignores [IconDisplayMode] where its two neighbours honour it: PLACEHOLDERS is a
     * choice about what *this* phone renders in a list of hundreds of rows, and says nothing about
     * a TV showing one channel at a time. Suppressing artwork there would leave the receiver on a
     * bare title for a setting the user picked to keep the channel list light.
     */
    fun castArtworkUrl(channel: M3uChannel, epgIconUrl: String?): String? =
        iconRepository.castArtworkUrl(channel.tvgLogo, epgIconUrl, channel.tvgId)

    fun setIconWifiOnly(enabled: Boolean) {
        preferences.iconWifiOnly = enabled
        _iconPrefetchState.update { it.copy(wifiOnly = enabled) }
    }

    /** [context] narrows a large playlist down to what's worth a background fetch right now - see
     * [PrefetchSelectionPolicy]. */
    fun triggerPrefetch(
        channels: List<M3uChannel>,
        iconDisplayMode: IconDisplayMode,
        epgIconUrlFor: (M3uChannel) -> String? = { null },
        context: PrefetchContext = PrefetchContext(),
    ) {
        // A previous playlist load may have happened while the network was down. Transient icon
        // failures and negative memory entries must not make the same URLs look permanently dead
        // when this fresh request arrives.
        iconRepository.retryTransientFailures()
        // SharedPreferences parses and rewrites its whole file. Keep this maintenance work off
        // the main thread: triggerPrefetch is called from PlaylistController's viewModelScope
        // callback immediately after a load, exactly when the first list frame is being rendered.
        // A child of the injected scope keeps it tied to the ViewModel lifecycle and makes the
        // dispatcher deterministic in tests.
        scope.launch(maintenanceDispatcher) {
            runCatchingNonFatal { iconRepository.pruneExpiredFailures() }
                .onFailure { error -> AppLog.w(TAG) { "Icon failure pruning failed: ${error.javaClass.simpleName}" } }
        }
        val previousWatcher: AutoCloseable?
        val registrationGeneration: Long
        synchronized(prefetchLifecycleLock) {
            if (disposed) return
            lastPrefetchChannels = channels
            lastEpgIconUrlFor = epgIconUrlFor
            lastIconDisplayMode = iconDisplayMode
            lastPrefetchContext = context
            watcherGeneration += 1
            registrationGeneration = watcherGeneration
            previousWatcher = networkWatcher
            networkWatcher = null
        }
        // Register/unregister may synchronously call framework code. Keep it outside the lock:
        // callbacks are allowed to re-enter startPrefetchJob(), whose guard uses the same lock.
        previousWatcher?.close()
        val newWatcher = iconPrefetcher.awaitNetwork {
            iconRepository.retryTransientFailures()
            startPrefetchJob()
        }
        val watcherIsCurrent = synchronized(prefetchLifecycleLock) {
            if (!disposed && watcherGeneration == registrationGeneration) {
                networkWatcher = newWatcher
                true
            } else {
                false
            }
        }
        if (!watcherIsCurrent) newWatcher.close()
        startPrefetchJob()
    }

    private fun startPrefetchJob() {
        val job = synchronized(prefetchLifecycleLock) {
            if (disposed || activeIconCacheClears.isNotEmpty()) return
            iconPrefetchJob?.cancel()
            prefetchGeneration += 1
            val generation = prefetchGeneration
            // The replacement may deliberately skip (DEFAULT mode, an empty list, low-end tier).
            // Clear the old generation's progress synchronously so that skip cannot leave a
            // progress indicator with no job behind it.
            _iconPrefetchState.update { it.copy(isRunning = false) }
            // Capture one coherent request under the same lock. Reading the mutable fields from
            // inside the lazily-started coroutine would let a concurrent trigger mix generations.
            val channels = lastPrefetchChannels
            val iconDisplayMode = lastIconDisplayMode
            val epgIconUrlFor = lastEpgIconUrlFor
            val context = lastPrefetchContext
            lateinit var newJob: Job
            newJob = scope.launch(start = CoroutineStart.LAZY) {
                runPrefetch(generation, channels, iconDisplayMode, epgIconUrlFor, context)
            }
            iconPrefetchJob = newJob
            activePrefetchJobs += newJob
            newJob.invokeOnCompletion {
                synchronized(prefetchLifecycleLock) {
                    activePrefetchJobs -= newJob
                    if (iconPrefetchJob === newJob) iconPrefetchJob = null
                }
            }
            newJob
        }
        job.start()
    }

    /** Cancels every in-flight generation without clearing the remembered request, so an idle
     * playback transition can re-arm it later. */
    fun cancelPrefetch() {
        val jobs = synchronized(prefetchLifecycleLock) {
            prefetchGeneration += 1
            _iconPrefetchState.update { it.copy(isRunning = false) }
            activePrefetchJobs.toList()
        }
        jobs.forEach(Job::cancel)
    }

    /**
     * Synchronously closes the door to every prefetch restart and returns the exact set of jobs
     * which may still be writing icon files. The caller must await the barrier before deleting the
     * directory and release it in a `finally` block afterwards.
     *
     * A set of clear IDs, rather than one Boolean, matters for two quick Clear taps: whichever
     * delete finishes first must not let a network callback restart prefetch during the other one.
     */
    fun beginIconCacheClear(): IconCacheClearBarrier {
        val jobs: List<Job>
        val watcher: AutoCloseable?
        val barrier: IconCacheClearBarrier
        synchronized(prefetchLifecycleLock) {
            val clearId = ++nextIconCacheClearId
            activeIconCacheClears += clearId
            // Invalidate a watcher registration that may currently be outside the lock in
            // awaitNetwork(), and forget the old request so clearing does not immediately
            // refill the cache when playback becomes idle.
            watcherGeneration += 1
            prefetchGeneration += 1
            lastPrefetchChannels = emptyList()
            watcher = networkWatcher
            networkWatcher = null
            jobs = activePrefetchJobs.toList()
            _iconPrefetchState.update { it.copy(isRunning = false) }
            barrier = IconCacheClearBarrier(clearId, jobs, activeIconResolutions.toList())
        }
        watcher?.close()
        jobs.forEach(Job::cancel)
        return barrier
    }

    fun finishIconCacheClear(barrier: IconCacheClearBarrier) {
        val waiters = synchronized(prefetchLifecycleLock) {
            activeIconCacheClears -= barrier.clearId
            if (activeIconCacheClears.isEmpty()) {
                iconCacheClearWaiters.toList().also { iconCacheClearWaiters.clear() }
            } else {
                emptyList()
            }
        }
        waiters.forEach { it.complete(Unit) }
    }

    fun invalidateMemoryCache() = iconRepository.invalidateMemoryCache()

    fun dispose() {
        val watcher: AutoCloseable?
        val jobs: List<Job>
        val waiters: List<CompletableDeferred<Unit>>
        synchronized(prefetchLifecycleLock) {
            disposed = true
            watcherGeneration += 1
            prefetchGeneration += 1
            activeIconCacheClears.clear()
            lastPrefetchChannels = emptyList()
            watcher = networkWatcher
            networkWatcher = null
            jobs = activePrefetchJobs.toList()
            waiters = iconCacheClearWaiters.toList()
            iconCacheClearWaiters.clear()
            _iconPrefetchState.update { it.copy(isRunning = false) }
        }
        watcher?.close()
        jobs.forEach(Job::cancel)
        waiters.forEach { it.complete(Unit) }
    }

    private fun hasPrefetchRequest(): Boolean = synchronized(prefetchLifecycleLock) {
        !disposed && lastPrefetchChannels.isNotEmpty()
    }

    private suspend fun acquireIconCacheLease(): CompletableDeferred<Unit> {
        while (true) {
            var lease: CompletableDeferred<Unit>? = null
            val waiter = synchronized(prefetchLifecycleLock) {
                if (disposed || activeIconCacheClears.isEmpty()) {
                    CompletableDeferred<Unit>().also {
                        activeIconResolutions += it
                        lease = it
                    }
                    null
                } else {
                    CompletableDeferred<Unit>().also(iconCacheClearWaiters::add)
                }
            }
            lease?.let { return it }
            checkNotNull(waiter)
            try {
                waiter.await()
            } finally {
                synchronized(prefetchLifecycleLock) { iconCacheClearWaiters -= waiter }
            }
        }
    }

    private fun releaseIconCacheLease(lease: CompletableDeferred<Unit>) {
        synchronized(prefetchLifecycleLock) { activeIconResolutions -= lease }
        lease.complete(Unit)
    }

    private suspend fun runPrefetch(
        generation: Long,
        channels: List<M3uChannel>,
        iconDisplayMode: IconDisplayMode,
        epgIconUrlFor: (M3uChannel) -> String?,
        context: PrefetchContext,
    ) {
        // LOW_END skips the bulk background pass entirely - only the lazy per-row fetch that
        // resolveChannelIcon already does as each row is actually composed (see ChannelIcon).
        // PlaybackActivity true means playback/casting started between triggerPrefetch and this
        // coroutine actually getting to run (or while queued behind the network watcher) -
        // the collector in init re-arms this once it goes idle again, so bailing here without
        // touching isRunning is safe.
        val selected = selectPrefetchChannels(channels, iconDisplayMode, context) ?: return

        val started = updatePrefetchState(generation) {
            it.copy(isRunning = true, completed = 0, total = selected.size)
        }
        if (!started) return
        val executed = runCatchingNonFatal {
            iconPrefetcher.prefetch(selected, preferences.iconWifiOnly, epgIconUrlFor) { progress ->
                updatePrefetchState(generation) {
                    it.copy(completed = progress.completed, total = progress.total)
                }
            }
        }.onFailure { error ->
            // One provider-controlled logo/callback or an OEM framework failure must not escape
            // the ViewModel's SupervisorJob and must not strand the global progress indicator.
            AppLog.w(TAG) { "Icon prefetch failed: ${error.javaClass.simpleName}" }
        }.getOrDefault(false)
        if (executed) {
            completePrefetch(generation)
        } else {
            updatePrefetchState(generation) { it.copy(isRunning = false) }
        }
    }

    private fun completePrefetch(generation: Long) {
        val completedCurrentGeneration = updatePrefetchState(generation) {
            it.copy(isRunning = false, updateReminderDue = false, completedRuns = it.completedRuns + 1)
        }
        if (completedCurrentGeneration) {
            preferences.lastIconPrefetchAtMillis = System.currentTimeMillis()
            // Prefetch may have just written icon files for channels that previously cached a
            // negative (no-icon) memory result - drop the cache so those channels get re-resolved
            // from disk. completedRuns above makes ChannelIcon actually re-render.
            iconRepository.invalidateMemoryCache()
            onPrefetchFinished()
        }
    }

    private fun selectPrefetchChannels(
        channels: List<M3uChannel>,
        iconDisplayMode: IconDisplayMode,
        context: PrefetchContext,
    ): List<M3uChannel>? {
        val limit = when (iconDisplayMode) {
            IconDisplayMode.CACHE -> PREFETCH_LIMIT
            IconDisplayMode.CACHE_LIMITED -> LIMITED_PREFETCH_LIMIT
            IconDisplayMode.PLACEHOLDERS -> null
        }
        val shouldSkip = limit == null || channels.isEmpty() ||
            context.deviceTier == DeviceTier.LOW_END ||
            PlaybackActivity.isActive.value
        return if (shouldSkip) {
            null
        } else {
            PrefetchSelectionPolicy.select(
                channels = channels,
                priority = PrefetchSelectionPolicy.PriorityChannels(
                    favoriteKeys = context.favoriteKeys,
                    lastWatchedKey = context.lastWatchedKey,
                    firstGroupChannels = context.firstGroupChannels,
                ),
                limit = checkNotNull(limit),
            ).takeIf(List<M3uChannel>::isNotEmpty)
        }
    }

    private inline fun updatePrefetchState(
        generation: Long,
        transform: (IconPrefetchUiState) -> IconPrefetchUiState,
    ): Boolean = synchronized(prefetchLifecycleLock) {
        if (disposed || activeIconCacheClears.isNotEmpty() || generation != prefetchGeneration) {
            false
        } else {
            _iconPrefetchState.update(transform)
            true
        }
    }

    /** Narrows [triggerPrefetch] down to the channels worth a bulk background fetch - see
     * [PrefetchSelectionPolicy]. [favoriteKeys] match [com.uacastplayer.favorites.FavoriteKey.of]. */
    data class PrefetchContext(
        val favoriteKeys: Set<String> = emptySet(),
        val lastWatchedKey: String? = null,
        val firstGroupChannels: List<M3uChannel> = emptyList(),
        val deviceTier: DeviceTier = DeviceTier.MID_RANGE,
    )

    class IconCacheClearBarrier internal constructor(
        internal val clearId: Long,
        private val jobs: List<Job>,
        private val resolutions: List<CompletableDeferred<Unit>>,
    ) {
        /** Deletion starts only after every captured background and foreground writer exits. */
        suspend fun awaitStopped() {
            jobs.joinAll()
            resolutions.awaitAll()
        }
    }

    private companion object {
        // Per-pass cap on how many channels the bulk background prefetch will fetch - the rest
        // still get their icon lazily, one row at a time, the first time they're actually scrolled
        // into view (see resolveChannelIcon). See docs/PERFORMANCE.md.
        const val PREFETCH_LIMIT = 300
        // Mid-range devices still benefit from logos, but fetching the full priority window on
        // every playlist refresh competes with scrolling and playback startup. Visible rows remain
        // lazy, so this is a bounded warm cache rather than a second full playlist download.
        const val LIMITED_PREFETCH_LIMIT = 48
    }
}
