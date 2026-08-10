package com.uacastplayer.premium

import kotlinx.coroutines.flow.StateFlow

/**
 * The one question the rest of the app is allowed to ask about access: *is this unlocked?*
 *
 * It knows nothing about billing, stores, purchases or prices - only about the current
 * [Entitlements]. That is what makes it testable with a value rather than a mock, and what keeps
 * the answer identical no matter where the entitlement came from: a real purchase, a trial, a
 * cached license read while offline, or the debug menu.
 *
 * There is deliberately no `isPremium` on this type. A boolean invites call sites to reason about
 * tiers themselves, which is exactly the sprawl this layer exists to prevent - the caller wants to
 * know whether *this* thing works, and nothing more.
 */
class FeatureManager(
    private val source: StateFlow<Entitlements>,
    private val storeCanSell: () -> Boolean = { true },
) {

    /** Current access, for UI that renders a badge or a lock and needs to recompose when it moves. */
    val entitlements: StateFlow<Entitlements> get() = source

    /** Whether [feature] can be used right now. */
    fun isUnlocked(feature: Feature): Boolean =
        !withholdingIsHonest() || feature in source.value.unlocked

    /**
     * Whether this build is in a position to take a feature away.
     *
     * **A build with no store must not withhold anything.** The trial granted on first launch runs
     * for 14 days and is granted exactly once, so with the gates live and
     * [PremiumAvailability.STORE_IS_LIVE] false, an install that has merely existed for a fortnight
     * would lose DLNA, multiple playlists, backups and parental control with no way whatsoever to
     * buy them back. A paywall in front of a closed till is not a business model.
     *
     * **Nor may a build whose store turned out to be empty**, which is the same sentence about the
     * day of release rather than the days before it. `STORE_IS_LIVE` is a promise made when the
     * source is edited; whether Play actually has products under these ids is only knowable once the
     * app is running on someone's phone, and Play reports a catalogue it does not recognise as an
     * ordinary empty success. So the constant opens the possibility of withholding and [storeCanSell]
     * decides whether it happens - see `LicenseStorage.storeHasEverOfferedProducts`, which latches
     * so that this cannot follow the network.
     *
     * The debug exception is what makes the gates testable rather than theoretical: a build with a
     * developer menu can put itself into any license state by hand (see [DeveloperMode]), which is
     * also the only build where seeing a lock is useful. Release builds cannot reach that branch -
     * nothing assigns [DeveloperMode.states] outside `src/debug`.
     *
     * In one line: *the only builds that may withhold are the ones that can also grant.*
     *
     * Deliberately here rather than in [FeaturePolicy]. That table answers "what is sold", which is
     * a fact about the product and does not change with the build; this answers "may we act on it
     * yet", which is a fact about the build. Folding the second into the first would also make
     * [Entitlements.FREE] - a value computed once, at class-initialization time - depend on whether
     * the debug Application had run yet.
     */
    private fun withholdingIsHonest(): Boolean = mayWithhold(
        storeIsLive = PremiumAvailability.STORE_IS_LIVE,
        storeCanSell = storeCanSell(),
        hasDeveloperMenu = DeveloperMode.isAvailable,
    )

    companion object {

        /**
         * The rule above, as a function of its three inputs rather than of the build it is running
         * in - which is the only way it can be asserted.
         *
         * [PremiumAvailability.STORE_IS_LIVE] is a compile-time constant and
         * [DeveloperMode.isAvailable] is true in every debug test run, so a test of `isUnlocked`
         * alone can only ever exercise one row of this table: the live-store combinations are not
         * reachable from a unit test at all, and the one that matters most - a store that is live
         * and has nothing in it - would go to production having never been executed.
         */
        internal fun mayWithhold(
            storeIsLive: Boolean,
            storeCanSell: Boolean,
            hasDeveloperMenu: Boolean,
        ): Boolean = (storeIsLive && storeCanSell) || hasDeveloperMenu
    }

    /** Whether [feature] is behind a paywall for this user - i.e. locked, and not merely absent.
     * The distinction the UI needs: a lock badge belongs on the first, nothing on the second. */
    fun isLocked(feature: Feature): Boolean = !isUnlocked(feature)
}
