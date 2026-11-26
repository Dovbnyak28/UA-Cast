package app.zorik.player.app

import app.zorik.player.guidedtour.GuidedTip
import app.zorik.player.guidedtour.GuidedTourAvailability
import app.zorik.player.guidedtour.GuidedTourPhase
import app.zorik.player.guidedtour.GuidedTourState
import app.zorik.player.guidedtour.GuidedTourStorage
import app.zorik.player.guidedtour.GuidedTourVersion
import app.zorik.player.guidedtour.IntroPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runs the first-launch intro and the one-time tips: which is showing, what the buttons do, and
 * what has already been seen.
 *
 * A plain class holding a [StateFlow], like the other controllers next to it, rather than a ViewModel
 * of its own - `AppViewModel` owns it and exposes its state, which is how every other controller in
 * this package is wired.
 *
 * *When* a tip shows is not decided here: that depends on what is on screen, which only the UI
 * knows (see `GuidedTourHost`). This class only answers whether a tip may show - not seen yet, and
 * nothing else up - and remembers it once dismissed.
 *
 * Everything here is synchronous. The persisted values live in the app's existing SharedPreferences
 * wrapper (see [GuidedTourStorage]), so there is nothing to await and no coroutine to leak.
 */
class GuidedTourController(
    private val storage: GuidedTourStorage,
    private val pages: List<IntroPage> = IntroPage.entries,
    private val tips: List<GuidedTip> = GuidedTip.entries,
    private val version: Int = GuidedTourVersion.CURRENT,
) {
    private val _state = MutableStateFlow(GuidedTourState(pages = pages))
    val state: StateFlow<GuidedTourState> = _state.asStateFlow()

    private val _hasSeenTour = MutableStateFlow(
        storage.guidedTourCompleted && storage.guidedTourVersion >= version,
    )

    /**
     * Whether this device has been through the current intro, for the Settings row's wording.
     *
     * A flow rather than a getter on [GuidedTourStorage]: SharedPreferences is not observable, so a
     * plain read is not a Compose state read either, and the row would have gone on saying "not
     * seen yet" until something unrelated recomposed Settings.
     */
    val hasSeenTour: StateFlow<Boolean> = _hasSeenTour.asStateFlow()

    private val _pendingTips = MutableStateFlow(unseen(storage.guidedTipsSeen))

    /** The tips not dismissed yet, in priority order. Empty once all of them have been seen - which
     * is also what lets the UI stop measuring tip targets altogether. */
    val pendingTips: StateFlow<List<GuidedTip>> = _pendingTips.asStateFlow()

    /**
     * Opens the intro on first launch, and again after an update that ships a newer edition. Does
     * nothing otherwise, and nothing at all if something is already showing - so a caller can invoke
     * it from an effect without guarding it.
     */
    fun offerOnLaunch() {
        if (_state.value.isVisible) return
        if (!GuidedTourAvailability.shouldOfferAutomatically(
                completed = storage.guidedTourCompleted,
                seenVersion = storage.guidedTourVersion,
                currentVersion = version,
            )
        ) {
            return
        }
        showIntro()
    }

    /**
     * The Settings entry: the intro again, and every tip back in play. Ignores everything
     * [offerOnLaunch] checks - a user who asked has answered the only question those checks exist
     * to ask.
     */
    fun startFromSettings() {
        storage.guidedTipsSeen = emptySet()
        _pendingTips.value = tips
        showIntro()
    }

    /** The intro's Next. The last page's Next finishes the intro rather than walking off the end. */
    fun introNext() {
        val current = _state.value
        if (current.phase != GuidedTourPhase.INTRO) return
        if (current.isLastIntroPage) finishIntro() else _state.value = current.copy(introPage = current.introPage + 1)
    }

    /** Back on any page but the first. On the first, back is leaving - see [finishIntro]. */
    fun introBack() {
        val current = _state.value
        if (current.phase != GuidedTourPhase.INTRO || current.introPage == 0) return
        _state.value = current.copy(introPage = current.introPage - 1)
    }

    /** Where a swipe settled. Coerced, so a pager that reports a stale index cannot leave the state
     * pointing past the last page. */
    fun goToIntroPage(index: Int) {
        val current = _state.value
        if (current.phase != GuidedTourPhase.INTRO || pages.isEmpty()) return
        val page = index.coerceIn(0, pages.lastIndex)
        if (page != current.introPage) _state.value = current.copy(introPage = page)
    }

    /**
     * Closing the intro by any route - Skip, Later, Add playlist, or the last page's button.
     *
     * Recorded the same way however it closed: the user has been asked, and the answer is final
     * until they ask for it again in Settings.
     */
    fun finishIntro() {
        if (_state.value.phase != GuidedTourPhase.INTRO) return
        storage.guidedTourCompleted = true
        storage.guidedTourVersion = version
        _hasSeenTour.value = true
        _state.value = GuidedTourState(pages = pages)
    }

    /**
     * Shows [tip] if it may show: nothing else is up, and it has not been dismissed. Returns whether
     * it did, so the caller can tell a refusal from a tip that is now showing.
     */
    fun showTip(tip: GuidedTip): Boolean {
        if (_state.value.isVisible || tip !in _pendingTips.value) return false
        _state.value = GuidedTourState(phase = GuidedTourPhase.TIP, tip = tip, pages = pages)
        return true
    }

    /** "Got it", a tap outside the card, or back. Any of them counts as seen. */
    fun dismissTip() {
        val tip = _state.value.tip ?: return
        markSeen(listOf(tip))
        _state.value = GuidedTourState(pages = pages)
    }

    /**
     * Something outside the app took over the screen - a video opened from a file manager. The intro
     * closes as if skipped; a tip that was up goes away *without* being marked seen, since nobody
     * read it, and will come back the next time its element appears.
     */
    fun interrupt() {
        when (_state.value.phase) {
            GuidedTourPhase.INTRO -> finishIntro()
            GuidedTourPhase.TIP -> _state.value = GuidedTourState(pages = pages)
            GuidedTourPhase.IDLE -> Unit
        }
    }

    /**
     * Everything marked seen at once: the intro and every tip. For test and benchmark set-up, where
     * an overlay appearing over the screen under test would swallow its taps.
     */
    fun dismissAll() {
        storage.guidedTourCompleted = true
        storage.guidedTourVersion = version
        _hasSeenTour.value = true
        markSeen(tips)
        _state.value = GuidedTourState(pages = pages)
    }

    private fun showIntro() {
        _state.value = GuidedTourState(phase = GuidedTourPhase.INTRO, introPage = 0, pages = pages)
    }

    private fun markSeen(seen: List<GuidedTip>) {
        val ids = storage.guidedTipsSeen + seen.map(GuidedTip::id)
        storage.guidedTipsSeen = ids
        _pendingTips.value = unseen(ids)
    }

    private fun unseen(seenIds: Set<String>): List<GuidedTip> = tips.filterNot { it.id in seenIds }
}
