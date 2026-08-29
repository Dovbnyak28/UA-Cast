package com.uacastplayer.app

import com.uacastplayer.data.epg.EpgFailureReason
import com.uacastplayer.data.epg.EpgOutcome
import com.uacastplayer.data.epg.EpgRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.epg.EpgRefreshPolicy
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.epg.EpgSourceAutoDetect
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.log.AppLog
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val EPG_TICK_MILLIS = 30_000L
private const val TAG = "EpgController"

/**
 * Owns the active EPG source/data - moved out of [com.uacastplayer.AppViewModel] as a move-only
 * split (see B1 in the consolidated fix plan); behavior is unchanged, this is still thin impure
 * glue over [EpgRepository].
 *
 * [onLoaded] is AppViewModel's hook into the cross-controller device-tier-recompute/cache-size-
 * refresh concerns that don't belong to EPG state itself.
 */
class EpgController(
    private val preferences: AppPreferences,
    private val epgRepository: EpgRepository,
    private val scope: CoroutineScope,
    private val onLoaded: () -> Unit,
    /** Whether the current network is one this app may spend ~50MB on - see [EpgRefreshPolicy].
     * A lambda rather than a ConnectivityManager so this class stays free of Android. */
    private val isUnmeteredNetwork: () -> Boolean = { false },
) {
    private val _epgState = MutableStateFlow(
        EpgUiState(selectedSource = preferences.epgSource, customUrl = preferences.customEpgUrl)
    )
    val epgState: StateFlow<EpgUiState> = _epgState.asStateFlow()

    /**
     * The one guide load allowed to be in flight, for the same reason [PlaylistController] keeps
     * one: every entry point here replaces the whole guide, so two of them racing means the one
     * that happens to finish *last* wins - not the one the user picked last.
     *
     * It was worse here than it ever was for playlists, because a load also writes the cache. There
     * is a single `epg_snapshot.bin` (see [com.uacastplayer.data.epg.EpgSnapshotStore]), so a slow
     * source that lands after a fast one replaced it both swapped the guide on screen for one the
     * user had already navigated away from and wrote it to disk, where it outlived the process:
     * Settings said one source, the guide was another's, and a restart restored the wrong one.
     *
     * [startTicking] is deliberately not routed through here - it is a permanent loop, not a load.
     */
    private var loadJob: Job? = null

    private fun launchLoad(block: suspend () -> Unit) {
        loadJob?.cancel()
        loadJob = scope.launch { block() }
    }

    /** Restores the cached snapshot at startup, or does an initial fetch if there isn't one -
     * called once from AppViewModel.init. */
    fun loadInitial() {
        launchLoad {
            // Before anything else, and regardless of whether this run downloads at all: a process
            // killed mid-download or mid-parse leaves a full feed behind in filesDir, where nothing
            // reclaims it. See EpgRepository.deleteStaleDownloads.
            // Safe to sit inside a cancellable job: EpgRepository.deleteStaleDownloads is one
            // non-suspending sweep inside a single withContext, so cancellation can only be
            // observed on the way out - after the sweep has already run.
            epgRepository.deleteStaleDownloads()
            val configuredUrl = preferences.customEpgUrl ?: preferences.epgSource.url
            val restored = epgRepository.restoreSnapshot(configuredUrl)
            if (restored != null) {
                applyEpgOutcome(restored.outcome)
                refreshIfFromAnEarlierDay(restored.savedAtMillis)
            } else {
                // No cached snapshot (fresh install, or cache was cleared) - without this, the
                // configured source is never fetched until the user manually reopens Settings and
                // reselects it, leaving the Home screen's "restoring" status stuck forever.
                _epgState.update { it.copy(isLoading = true) }
                val customUrl = preferences.customEpgUrl
                val outcome = if (customUrl != null) {
                    epgRepository.loadFromUrl(customUrl)
                } else {
                    epgRepository.loadFromSource(preferences.epgSource)
                }
                applyEpgOutcome(outcome)
            }
        }
    }

    /**
     * Re-downloads a guide that was cached on an earlier day, behind the restored one.
     *
     * Deliberately after the snapshot has already been applied, and without touching `isLoading`:
     * the user has a usable guide from the first frame and this replaces it quietly if it arrives.
     * A failed refresh is logged and otherwise ignored - it must not set `hasError`, because the
     * guide on screen is real and telling somebody it is broken would be false.
     */
    private suspend fun refreshIfFromAnEarlierDay(savedAtMillis: Long) {
        val shouldRefresh = EpgRefreshPolicy.shouldRefresh(
            savedAtMillis = savedAtMillis,
            nowMillis = System.currentTimeMillis(),
            zoneId = ZoneId.systemDefault(),
            isUnmetered = isUnmeteredNetwork(),
        )
        if (!shouldRefresh) return
        val customUrl = preferences.customEpgUrl
        val outcome = if (customUrl != null) {
            epgRepository.loadFromUrl(customUrl)
        } else {
            epgRepository.loadFromSource(preferences.epgSource)
        }
        if (outcome is EpgOutcome.Loaded) {
            applyEpgOutcome(outcome)
        } else {
            AppLog.w(TAG) { "Keeping the cached guide: ${EpgFailureReason.of(outcome)}" }
        }
    }

    /** Refreshes [EpgUiState.nowMillis] every [EPG_TICK_MILLIS] so current/next-programme
     * lookups stay live - called once from AppViewModel.init. */
    fun startTicking() {
        scope.launch {
            while (isActive) {
                delay(EPG_TICK_MILLIS)
                _epgState.update { it.copy(nowMillis = System.currentTimeMillis()) }
            }
        }
    }

    fun selectEpgSource(source: EpgSource) {
        preferences.epgSource = source
        preferences.customEpgUrl = null
        preferences.hasChosenEpgSource = true
        _epgState.update {
            it.copy(selectedSource = source, customUrl = null, suggestedUrl = null, isLoading = true, hasError = false)
        }
        launchLoad {
            applyEpgOutcome(epgRepository.loadFromSource(source))
        }
    }

    /** Accepts the "EPG address found in playlist" suggestion (see [EpgSourceAutoDetect]) - counts
     * as the user's own manual choice from here on, same as [selectEpgSource]. */
    fun useSuggestedEpgUrl() {
        val url = epgState.value.suggestedUrl ?: return
        applyCustomEpgUrl(url, markChosen = true)
    }

    fun applyCustomEpgUrl(url: String, markChosen: Boolean) {
        preferences.customEpgUrl = url
        if (markChosen) preferences.hasChosenEpgSource = true
        _epgState.update { it.copy(customUrl = url, suggestedUrl = null, isLoading = true, hasError = false) }
        launchLoad {
            applyEpgOutcome(epgRepository.loadFromUrl(url))
        }
    }

    /** Only ever called right after an actual playlist (re)load - see
     * [PlaylistController]'s `fromCache` gate via AppViewModel - so a startup cache restore can't
     * re-trigger this on every launch. */
    fun handleEpgAutoDetect(epgUrls: List<String>) {
        val currentUrl = epgState.value.customUrl
        when (val action = EpgSourceAutoDetect.decide(epgUrls, preferences.hasChosenEpgSource, currentUrl)) {
            is EpgSourceAutoDetect.Action.Apply -> applyCustomEpgUrl(action.url, markChosen = false)
            is EpgSourceAutoDetect.Action.Suggest -> _epgState.update { it.copy(suggestedUrl = action.url) }
            EpgSourceAutoDetect.Action.Ignore -> Unit
        }
    }

    private fun applyEpgOutcome(outcome: EpgOutcome) {
        // The outcome names the problem - an HTTP code, an exception class, a size refusal - and
        // until now every one of them was flattened into `hasError = true` and never written down.
        // See EpgFailureReason for the field report where that silence was met.
        val failure = EpgFailureReason.of(outcome)
        if (failure != null) AppLog.w(TAG) { "EPG did not load: $failure" }
        _epgState.update { current ->
            when (outcome) {
                is EpgOutcome.Loaded ->
                    current.copy(data = outcome.data, isLoading = false, hasError = false, lastFailure = null)
                else -> current.copy(isLoading = false, hasError = true, lastFailure = failure)
            }
        }
        onLoaded()
    }
}
