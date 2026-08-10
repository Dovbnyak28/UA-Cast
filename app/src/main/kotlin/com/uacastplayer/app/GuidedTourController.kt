package com.uacastplayer.app

import com.uacastplayer.guidedtour.GuidedTourAvailability
import com.uacastplayer.guidedtour.GuidedTourPhase
import com.uacastplayer.guidedtour.GuidedTourState
import com.uacastplayer.guidedtour.GuidedTourStep
import com.uacastplayer.guidedtour.GuidedTourSteps
import com.uacastplayer.guidedtour.GuidedTourStorage
import com.uacastplayer.guidedtour.GuidedTourVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runs the guided tour: which step is showing, what Next/Back/Skip do, and when the tour stops
 * offering itself.
 *
 * A plain class holding a [StateFlow], like `UpdateController` next to it, rather than a ViewModel
 * of its own - `AppViewModel` owns it and exposes its state, which is how every other controller in
 * this package is wired.
 *
 * Everything here is synchronous. The two persisted values are scalars in the app's existing
 * SharedPreferences wrapper (see [GuidedTourStorage]), so there is nothing to await and no
 * coroutine to leak - which also means the tour cannot delay a frame while the user is waiting to
 * see the next card.
 */
class GuidedTourController(
    private val storage: GuidedTourStorage,
    private val steps: List<GuidedTourStep> = GuidedTourSteps.DEFAULT,
    private val version: Int = GuidedTourVersion.CURRENT,
) {
    private val _state = MutableStateFlow(GuidedTourState())
    val state: StateFlow<GuidedTourState> = _state.asStateFlow()

    private val _hasSeenTour = MutableStateFlow(
        storage.guidedTourCompleted && storage.guidedTourVersion >= version,
    )

    /**
     * Whether this device has been through the current edition, for the Settings row's wording.
     *
     * A flow rather than a getter on [GuidedTourStorage]: SharedPreferences is not observable, so a
     * plain read is not a Compose state read either, and the row would have gone on saying "not
     * seen yet" until something unrelated recomposed Settings.
     */
    val hasSeenTour: StateFlow<Boolean> = _hasSeenTour.asStateFlow()

    /**
     * Opens the welcome card on first launch, and again after an update that ships a newer edition
     * of the tour. Does nothing otherwise, and nothing at all if the tour is already showing - so a
     * caller can invoke it from an effect without guarding it.
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
        show()
    }

    /**
     * The Settings entry. Ignores everything [offerOnLaunch] checks - a user who asked for the tour
     * has answered the only question those checks exist to ask.
     */
    fun startFromSettings() = show()

    /** Welcome -> first step, and step -> step. The last step's Next ends on the "done" card rather
     * than closing, so finishing the tour looks different from abandoning it. */
    fun next() {
        val current = _state.value
        when (current.phase) {
            // An empty catalogue - which only a bad edit to GuidedTourSteps can produce - skips
            // straight to the end. A STEPS phase with nothing to show is a card with no text on it.
            GuidedTourPhase.WELCOME -> _state.value = if (current.steps.isEmpty()) {
                current.copy(phase = GuidedTourPhase.DONE)
            } else {
                current.copy(phase = GuidedTourPhase.STEPS, stepIndex = 0)
            }
            GuidedTourPhase.STEPS -> _state.value = if (current.stepIndex >= current.steps.lastIndex) {
                current.copy(phase = GuidedTourPhase.DONE)
            } else {
                current.copy(stepIndex = current.stepIndex + 1)
            }
            // Nothing follows the done card, and there is no tour to advance when idle.
            GuidedTourPhase.DONE, GuidedTourPhase.IDLE -> Unit
        }
    }

    /** Back off the first step returns to the welcome card rather than doing nothing - a control
     * that is visible and inert is worse than one that goes somewhere. */
    fun back() {
        val current = _state.value
        when {
            current.phase == GuidedTourPhase.DONE -> _state.value = if (current.steps.isEmpty()) {
                current.copy(phase = GuidedTourPhase.WELCOME, stepIndex = 0)
            } else {
                current.copy(phase = GuidedTourPhase.STEPS, stepIndex = current.steps.lastIndex)
            }
            current.phase == GuidedTourPhase.STEPS && current.stepIndex > 0 ->
                _state.value = current.copy(stepIndex = current.stepIndex - 1)
            current.phase == GuidedTourPhase.STEPS ->
                _state.value = current.copy(phase = GuidedTourPhase.WELCOME, stepIndex = 0)
            else -> Unit
        }
    }

    /**
     * Leaving early. Recorded exactly the same way as finishing: the user has been asked, and the
     * answer was no. They can still start it from Settings, which is the difference between
     * remembering a decision and hiding a feature.
     */
    fun skip() {
        remember()
        _state.value = GuidedTourState()
    }

    /** The done card's button. */
    fun complete() {
        remember()
        _state.value = GuidedTourState()
    }

    private fun show() {
        _state.value = GuidedTourState(phase = GuidedTourPhase.WELCOME, stepIndex = 0, steps = steps)
    }

    private fun remember() {
        storage.guidedTourCompleted = true
        storage.guidedTourVersion = version
        _hasSeenTour.value = true
    }
}
