package com.uacastplayer.premium

import com.uacastplayer.premium.billing.BillingConnectionState
import com.uacastplayer.premium.billing.BillingProduct
import com.uacastplayer.premium.billing.PurchaseResult

/**
 * Everything the premium UI needs, bundled the way [com.uacastplayer.update.UpdateSectionState] is:
 * the Settings section, the full screen, the sheet and the banner are four views of one thing, and
 * threading a dozen parameters for it through `RootScaffold` would only lengthen a signature that
 * is already long.
 *
 * @param products what the store offers, priced by the store. Empty when there is no store to ask -
 *   which is the truthful state until this app is published, and the reason the screen has a line
 *   saying so rather than an empty list and a buy button that does nothing.
 * @param lastOutcome how the most recent purchase or restore ended, or null when nothing has been
 *   attempted since the last one was read. Money is the one place in this app where silence is not
 *   an acceptable answer: a card that was declined, a store that could not be reached, and an
 *   account that simply owns nothing all look identical to a user who is told nothing, and the only
 *   thing left to try is tapping the button again.
 *   It is cleared when the next attempt starts rather than on a timer or on being drawn: a message
 *   that vanishes while being read is the same as no message, and a countdown would have to be
 *   longer than anyone waits before tapping again.
 * @param isPurchasing an attempt is with the store right now. Every buy control is disabled while
 *   it is true, and that is not decoration: Play reports a purchase through a client-wide listener
 *   rather than through the call that started it, so a second attempt launched before the first is
 *   answered leaves the first waiting for a reply that is now addressed to the second. The button
 *   also has nothing to say while Play's own sheet is opening, which on a slow phone is long enough
 *   to press again.
 * @param developerStates license states the debug build can be forced into; empty in a release
 *   build, where the code that would fill them is not compiled (see [DeveloperMode]).
 */
data class PremiumSectionState(
    val entitlements: Entitlements,
    val products: List<BillingProduct>,
    val onPurchase: (BillingProduct) -> Unit,
    val onRestore: () -> Unit,
    val lastOutcome: PurchaseResult? = null,
    val isPurchasing: Boolean = false,
    /** Whether a store can be reached at all, which is what tells "nothing published yet" apart
     * from "this device has no Google Play" when [products] is empty - see [StoreAbsence]. */
    val connection: BillingConnectionState = BillingConnectionState.DISCONNECTED,
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
