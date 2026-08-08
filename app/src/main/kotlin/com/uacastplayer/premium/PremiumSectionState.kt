package com.uacastplayer.premium

import com.uacastplayer.premium.billing.BillingConnectionState
import com.uacastplayer.premium.billing.BillingProduct

/**
 * Everything the premium UI needs, bundled the way [com.uacastplayer.update.UpdateSectionState] is:
 * the Settings section, the full screen, the sheet and the banner are four views of one thing, and
 * threading a dozen parameters for it through `RootScaffold` would only lengthen a signature that
 * is already long.
 *
 * @param products what the store offers, priced by the store. Empty when there is no store to ask -
 *   which is the truthful state until this app is published, and the reason the screen has a line
 *   saying so rather than an empty list and a buy button that does nothing.
 * @param developerStates license states the debug build can be forced into; empty in a release
 *   build, where the code that would fill them is not compiled (see [DeveloperMode]).
 */
data class PremiumSectionState(
    val entitlements: Entitlements,
    val connection: BillingConnectionState,
    val products: List<BillingProduct>,
    val onPurchase: (BillingProduct) -> Unit,
    val onRestore: () -> Unit,
    val developerStates: List<String> = emptyList(),
    val onDeveloperStateSelected: (String) -> Unit = {},
) {

    /**
     * Whole days left before the current entitlement lapses, or null when nothing is counting down.
     *
     * Rounded up, because "0 days left" on a trial that still has hours in it is a lie in the
     * direction that costs the user something.
     */
    fun daysRemaining(nowMillis: Long): Int? {
        val expiry = entitlements.license.expiresAtMillis
        if (expiry == null || entitlements.hasLapsed) return null
        val remaining = expiry - nowMillis
        return if (remaining <= 0) null else ((remaining + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY).toInt()
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
