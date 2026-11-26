package app.zorik.player.guidedtour

/**
 * What Settings needs to offer the intro and tips again, bundled the way
 * [app.zorik.player.premium.PremiumSectionState] is - one parameter through `RootScaffold` rather
 * than two, on a signature that is already long enough.
 */
data class GuidedTourSectionState(
    /** True once the user has finished or skipped the current edition; the Settings row says so, so
     * the button does not look like something they have never used. */
    val hasSeenTour: Boolean,
    val onStartTour: () -> Unit,
)
