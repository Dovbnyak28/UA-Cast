package com.uacastplayer.guidedtour

/**
 * Where the tour is.
 *
 * [WELCOME] and [DONE] are phases rather than two more entries in [GuidedTourSteps.DEFAULT]: they
 * have no target, no progress position and different buttons, so folding them into the step list
 * would mean every step carrying fields only those two ever use - and the progress indicator would
 * have to subtract them back out to say "3 / 7".
 */
enum class GuidedTourPhase { IDLE, WELCOME, STEPS, DONE }

/**
 * The whole of the tour's state, and the only thing the UI reads.
 *
 * [stepIndex] is always a valid index into [steps] while [phase] is [GuidedTourPhase.STEPS] -
 * [com.uacastplayer.app.GuidedTourController] is what guarantees that, by moving to
 * [GuidedTourPhase.DONE] instead of walking off the end. [currentStep] returning null rather than
 * throwing is the second half of that guarantee: a state that somehow did go out of range renders
 * as no tour at all, not a crash on a screen the user was only being shown how to use.
 */
data class GuidedTourState(
    val phase: GuidedTourPhase = GuidedTourPhase.IDLE,
    val stepIndex: Int = 0,
    val steps: List<GuidedTourStep> = emptyList(),
) {
    val isVisible: Boolean get() = phase != GuidedTourPhase.IDLE

    val currentStep: GuidedTourStep?
        get() = if (phase == GuidedTourPhase.STEPS) steps.getOrNull(stepIndex) else null

    /** Only from the second step on - there is nothing behind the first one but the welcome card,
     * which Back does return to. */
    val canGoBack: Boolean get() = phase == GuidedTourPhase.STEPS

    /** 1-based, for "3 / 7". Meaningless outside [GuidedTourPhase.STEPS], where the indicator is
     * not drawn at all. */
    val stepNumber: Int get() = stepIndex + 1

    val stepCount: Int get() = steps.size
}
