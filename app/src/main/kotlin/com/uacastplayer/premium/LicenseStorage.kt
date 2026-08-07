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
}
