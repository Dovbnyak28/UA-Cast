package com.uacastplayer.app

import com.uacastplayer.data.epg.EpgOutcome
import com.uacastplayer.data.epg.EpgRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.epg.EpgSourceAutoDetect
import com.uacastplayer.epg.EpgUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val EPG_TICK_MILLIS = 30_000L

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
) {
    private val _epgState = MutableStateFlow(
        EpgUiState(selectedSource = preferences.epgSource, customUrl = preferences.customEpgUrl)
    )
    val epgState: StateFlow<EpgUiState> = _epgState.asStateFlow()

    var programmeCount: Int = 0
        private set

    /** Restores the cached snapshot at startup, or does an initial fetch if there isn't one -
     * called once from AppViewModel.init. */
    fun loadInitial() {
        scope.launch {
            val restored = epgRepository.restoreSnapshot()
            if (restored != null) {
                applyEpgOutcome(restored)
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
        scope.launch {
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
        scope.launch {
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
        _epgState.update { current ->
            when (outcome) {
                is EpgOutcome.Loaded -> current.copy(data = outcome.data, isLoading = false, hasError = false)
                else -> current.copy(isLoading = false, hasError = true)
            }
        }
        if (outcome is EpgOutcome.Loaded) {
            programmeCount = outcome.data.programmesByChannelId.values.sumOf { it.size }
        }
        onLoaded()
    }
}
