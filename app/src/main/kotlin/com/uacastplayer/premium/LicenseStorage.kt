package com.uacastplayer.premium

/**
 * Where the last known license is kept between launches, as an interface so the premium domain
 * never has to know that the answer is `SharedPreferences` - the same shape
 * [com.uacastplayer.update.UpdateCheckStorage] uses.
 *
 * This is what makes the app usable on a plane. A store cannot always be reached, and treating
 * "cannot ask" as "has not paid" would take a paid feature away from someone in an aeroplane seat.
 * The cached license stands in until the store can be reached and contradicts it.
 *
 * It is not a security boundary. Anything on the device can be edited by whoever owns the device,
 * and pretending otherwise leads to elaborate obfuscation that stops nobody. Real enforcement is a
 * server's job - which is why `BillingProvider` has room for one.
 */
interface LicenseStorage {

    /** The last license this device saw, or null if it has never held one. */
    var storedLicense: License?

    /**
     * Whether a store has ever, on this install, answered with a catalogue that had something in it.
     *
     * This is not about a purchase - it is about whether the till is open at all. A product id that
     * Play Console does not know is not an error: Play answers the query without it. So a build with
     * [PremiumAvailability.STORE_IS_LIVE] flipped a day too early, or with one id misspelled, reaches
     * users as an app that has taken features away and offers nothing to buy them back with, and
     * says nothing about why. Recorded once and kept, because the honest answer to "can this device
     * buy anything" is not available on an aeroplane, and a plane is not a reason to hand the app
     * out for free either. See `FeatureManager.isUnlocked`.
     */
    var storeHasEverOfferedProducts: Boolean

    /**
     * The latest wall clock this app has ever observed, used to notice the system clock moving
     * backwards - see [TrialEligibilityPolicy.clampToHighWaterMark].
     *
     * Zero means nothing has been recorded yet, which is any moment before the first entitlement
     * is resolved and is never treated as evidence about the clock.
     */
    var clockHighWaterMark: Long
}
