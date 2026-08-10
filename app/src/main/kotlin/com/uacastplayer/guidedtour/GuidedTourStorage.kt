package com.uacastplayer.guidedtour

/**
 * The two values the tour remembers, as an interface so
 * [com.uacastplayer.app.GuidedTourController] can be tested without Android's SharedPreferences.
 * Implemented by [com.uacastplayer.data.prefs.AppPreferences], the same way
 * [com.uacastplayer.update.UpdateCheckStorage] and [com.uacastplayer.premium.LicenseStorage] are.
 */
interface GuidedTourStorage {

    /** Set when the user finishes *or* skips. Skipping is a decision, and asking again on the next
     * launch would be ignoring it. */
    var guidedTourCompleted: Boolean

    /** Which edition of the tour the flag above refers to. 0 means "no tour has ever been seen",
     * which is also what a device that predates this feature reads. */
    var guidedTourVersion: Int
}

/**
 * Whether the tour should open by itself.
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
