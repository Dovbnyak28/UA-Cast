package com.uacastplayer.app

import com.uacastplayer.data.icons.IconPrefetcher
import com.uacastplayer.data.icons.IconRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.icons.LogoUpdateReminder
import com.uacastplayer.icons.PrefetchSelectionPolicy
import com.uacastplayer.performance.DeviceTier
import com.uacastplayer.player.PlaybackActivity
import com.uacastplayer.playlist.M3uChannel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) {
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

    private var unmeteredNetworkWatcher: AutoCloseable? = null
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
                    iconPrefetchJob?.cancel()
                } else if (lastPrefetchChannels.isNotEmpty()) {
                    startPrefetchJob()
                }
            }
        }
    }

    fun customIconSources(): List<String> = iconRepository.customIconSources()

    fun addCustomIconSource(url: String) = iconRepository.addCustomIconSource(url)

    fun removeCustomIconSource(url: String) = iconRepository.removeCustomIconSource(url)

    suspend fun resolveChannelIcon(channel: M3uChannel, iconDisplayMode: IconDisplayMode, epgIconUrl: String?): File? {
        if (iconDisplayMode == IconDisplayMode.PLACEHOLDERS) return null
        return iconRepository.resolveIconFile(channel.tvgLogo, epgIconUrl, channel.tvgId)
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

    /** For GroupIconCollage - a disk-cache-only lookup, never fetches. See [IconRepository.cachedIconFile]. */
    suspend fun cachedChannelIcon(channel: M3uChannel, iconDisplayMode: IconDisplayMode, epgIconUrl: String?): File? {
        if (iconDisplayMode == IconDisplayMode.PLACEHOLDERS) return null
        return iconRepository.cachedIconFile(channel.tvgLogo, epgIconUrl, channel.tvgId)
    }

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
        // Once per playlist load/refresh - see IconFailureStore.pruneExpiredFailures's doc.
        iconRepository.pruneExpiredFailures()
        lastPrefetchChannels = channels
        lastEpgIconUrlFor = epgIconUrlFor
        lastIconDisplayMode = iconDisplayMode
        lastPrefetchContext = context
        unmeteredNetworkWatcher?.close()
        unmeteredNetworkWatcher = iconPrefetcher.awaitUnmeteredNetwork { startPrefetchJob() }
        startPrefetchJob()
    }

    private fun startPrefetchJob() {
        iconPrefetchJob?.cancel()
        iconPrefetchJob = scope.launch {
            runPrefetch(lastPrefetchChannels, lastIconDisplayMode, lastEpgIconUrlFor, lastPrefetchContext)
        }
    }

    /** Cancels any in-flight prefetch without waiting for it to actually stop - pair with
     * [awaitPrefetchStopped] when the caller needs to be sure it has (e.g. before deleting the
     * icon cache directory it may still be writing into). */
    fun cancelPrefetch() {
        iconPrefetchJob?.cancel()
        _iconPrefetchState.update { it.copy(isRunning = false) }
    }

    /** Cancellation is cooperative, not instant - joins the prefetch coroutine so the caller knows
     * it has actually unwound. */
    suspend fun awaitPrefetchStopped() {
        iconPrefetchJob?.join()
    }

    fun invalidateMemoryCache() = iconRepository.invalidateMemoryCache()

    fun dispose() {
        unmeteredNetworkWatcher?.close()
    }

    private suspend fun runPrefetch(
        channels: List<M3uChannel>,
        iconDisplayMode: IconDisplayMode,
        epgIconUrlFor: (M3uChannel) -> String?,
        context: PrefetchContext,
    ) {
        // LOW_END skips the bulk background pass entirely - only the lazy per-row fetch that
        // resolveChannelIcon already does as each row is actually composed (see ChannelIcon).
        // PlaybackActivity true means playback/casting started between triggerPrefetch and this
        // coroutine actually getting to run (or while queued behind the unmetered-network watcher) -
        // the collector in init re-arms this once it goes idle again, so bailing here without
        // touching isRunning is safe.
        val shouldSkip = channels.isEmpty() ||
            iconDisplayMode != IconDisplayMode.CACHE ||
            context.deviceTier == DeviceTier.LOW_END ||
            PlaybackActivity.isActive.value
        if (shouldSkip) return

        val selected = PrefetchSelectionPolicy.select(
            channels = channels,
            priority = PrefetchSelectionPolicy.PriorityChannels(
                favoriteKeys = context.favoriteKeys,
                lastWatchedKey = context.lastWatchedKey,
                firstGroupChannels = context.firstGroupChannels,
            ),
            limit = PREFETCH_LIMIT,
        )
        if (selected.isEmpty()) return

        _iconPrefetchState.update { it.copy(isRunning = true, completed = 0, total = selected.size) }
        iconPrefetcher.prefetch(selected, preferences.iconWifiOnly, epgIconUrlFor) { progress ->
            _iconPrefetchState.update { it.copy(completed = progress.completed, total = progress.total) }
        }
        preferences.lastIconPrefetchAtMillis = System.currentTimeMillis()
        _iconPrefetchState.update {
            it.copy(isRunning = false, updateReminderDue = false, completedRuns = it.completedRuns + 1)
        }
        // Prefetch may have just written icon files for channels that previously cached a negative
        // (no-icon) memory result - drop the cache so those channels get re-resolved from disk.
        // completedRuns above is the signal ChannelIcon's refreshKey uses to actually re-render.
        iconRepository.invalidateMemoryCache()
        onPrefetchFinished()
    }

    /** Narrows [triggerPrefetch] down to the channels worth a bulk background fetch - see
     * [PrefetchSelectionPolicy]. [favoriteKeys] match [com.uacastplayer.favorites.FavoriteKey.of]. */
    data class PrefetchContext(
        val favoriteKeys: Set<String> = emptySet(),
        val lastWatchedKey: String? = null,
        val firstGroupChannels: List<M3uChannel> = emptyList(),
        val deviceTier: DeviceTier = DeviceTier.MID_RANGE,
    )

    private companion object {
        // Per-pass cap on how many channels the bulk background prefetch will fetch - the rest
        // still get their icon lazily, one row at a time, the first time they're actually scrolled
        // into view (see resolveChannelIcon). See docs/PERFORMANCE.md.
        const val PREFETCH_LIMIT = 300
    }
}
