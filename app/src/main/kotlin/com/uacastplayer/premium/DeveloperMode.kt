package com.uacastplayer.premium

import com.uacastplayer.premium.billing.BillingProvider

/**
 * The seam a debug build uses to drive every license state by hand, and the reason a release build
 * cannot.
 *
 * Both fields are filled in by code that lives in `src/debug` only. In a release build nothing
 * assigns them - not because a flag says so, but because the class that would do the assigning is
 * not compiled into that variant at all. [states] stays empty, Settings therefore renders no
 * developer section, and there is no path to [LicenseTier.ADMIN] in the shipped APK.
 *
 * That is deliberately stronger than `if (BuildConfig.DEBUG)`. A flag leaves the granting code
 * inside the APK, visible to anyone who decompiles it and one inverted condition away from handing
 * every user full access.
 */
object DeveloperMode {

    /** Names of the license states this build can be forced into; empty in a release build. */
    var states: List<String> = emptyList()

    /**
     * Puts the app into [state]: writes whatever stored license that state implies (a trial and an
     * expired subscription are not purchases, so no store can express them) and returns the
     * provider to listen to from then on.
     *
     * Null in a release build.
     */
    var apply: ((state: String, storage: LicenseStorage) -> BillingProvider)? = null

    /** Whether this build has a developer menu at all - the only thing the UI needs to ask. */
    val isAvailable: Boolean get() = states.isNotEmpty() && apply != null
}
