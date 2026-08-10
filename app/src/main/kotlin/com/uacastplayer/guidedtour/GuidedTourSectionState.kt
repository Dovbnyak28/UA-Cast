package com.uacastplayer.guidedtour

/**
 * What Settings needs to offer the tour again, bundled the way
 * [com.uacastplayer.update.UpdateSectionState] is - one parameter through `RootScaffold` rather
 * than two, on a signature that is already long enough.
 */
data class GuidedTourSectionState(
    /** True once the user has finished or skipped the current edition; the Settings row says so, so
     * the button does not look like something they have never used. */
    val hasSeenTour: Boolean,
    val onStartTour: () -> Unit,
)
