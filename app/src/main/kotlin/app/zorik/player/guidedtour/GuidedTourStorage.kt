package app.zorik.player.guidedtour

/**
 * What the intro and the tips remember, as an interface so
 * [app.zorik.player.app.GuidedTourController] can be tested without Android's SharedPreferences.
 * Implemented by [app.zorik.player.data.prefs.AppPreferences], the same way
 * [app.zorik.player.premium.LicenseStorage] is.
 */
interface GuidedTourStorage {

    /** Set when the user finishes *or* skips the intro. Skipping is a decision, and asking again on
     * the next launch would be ignoring it. */
    var guidedTourCompleted: Boolean

    /** Which edition of the intro the flag above refers to. 0 means "never seen", which is also what
     * a device that predates this feature reads. */
    var guidedTourVersion: Int

    /** The [GuidedTip.id]s already dismissed. A tip in here never shows again until Settings'
     * "show again" clears the set. */
    var guidedTipsSeen: Set<String>
}

/**
 * Whether the intro should open by itself.
 *
 * Two rules, and the second is the reason the version is stored at all:
 *
 * - never seen it -> offer it;
 * - seen an older edition than this build ships -> offer it again, once.
 *
 * A user who has seen the current edition is never interrupted; Settings is how they get it back.
 */
object GuidedTourAvailability {

    fun shouldOfferAutomatically(
        completed: Boolean,
        seenVersion: Int,
        currentVersion: Int = GuidedTourVersion.CURRENT,
    ): Boolean = !completed || seenVersion < currentVersion
}
