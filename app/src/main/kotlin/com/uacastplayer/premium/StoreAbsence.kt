package com.uacastplayer.premium

import com.uacastplayer.premium.billing.BillingConnectionState

/**
 * Why there is nothing to buy, when there is nothing to buy.
 *
 * The premium screen used to have one sentence for all of it - *"this app is not published in a
 * store"* - which was true while that was the only way to get an empty catalogue. It stops being
 * true the day the app is published: a viewer on an Android TV box without Google Play would read
 * that the app is unpublished, which is simply false, and it is the app telling them so on the
 * screen where it asks to be trusted with money.
 *
 * The three cases below need three different sentences because the reader's situation differs in
 * each: one is waiting for a release, one will never have a store at all, and one is looking at a
 * misconfiguration nobody has noticed yet.
 */
enum class StoreAbsence {

    /** No store has been switched on in this build - see [PremiumAvailability]. Temporary, and true
     * of every copy in existence until the day it is not. */
    BUILD_HAS_NO_STORE,

    /**
     * Google Play is not on this device: an Android TV box with no GMS, a de-Googled phone, a
     * sideloaded APK on hardware Play never shipped to.
     *
     * Permanent, and not the user's problem to solve - which is why premium stays open here rather
     * than closing after a grace period. Withholding would take features from the one audience that
     * has been shown to have no way of buying them, and this app supports that audience on purpose:
     * see the leanback entries in the manifest and `docs/TV_SUPPORT.md`.
     */
    DEVICE_HAS_NO_STORE,

    /**
     * Play answered, and had nothing to sell.
     *
     * Almost always a console that is not ready: a product still in draft, one id spelled
     * differently, a release live before the track it belongs to went out. The user can do nothing
     * about it either, so the gates stay open (see `FeatureManager`) - but unlike the case above
     * this one is a mistake somebody should fix, which is why it is worth telling apart.
     */
    STORE_OFFERS_NOTHING,
    ;

    companion object {

        /**
         * Which of the three applies, or null when there is a catalogue and the question does not
         * arise.
         *
         * Deliberately reads [storeIsLive] rather than only the connection: a build with no store
         * uses `FakeBillingProvider`, which truthfully reports [BillingConnectionState.UNAVAILABLE]
         * too. Without that first check every pre-release copy would tell its user their device has
         * no Google Play, which is the same kind of wrong sentence in the other direction.
         */
        fun of(
            storeIsLive: Boolean,
            connection: BillingConnectionState,
            hasProducts: Boolean,
        ): StoreAbsence? = when {
            hasProducts -> null
            !storeIsLive -> BUILD_HAS_NO_STORE
            connection == BillingConnectionState.UNAVAILABLE -> DEVICE_HAS_NO_STORE
            else -> STORE_OFFERS_NOTHING
        }
    }
}
