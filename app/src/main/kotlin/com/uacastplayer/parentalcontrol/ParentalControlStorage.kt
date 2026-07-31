package com.uacastplayer.parentalcontrol

/**
 * Persistence seam for the locked-channel set, implemented for real by
 * [com.uacastplayer.data.parentalcontrol.ParentalControlStore]. See [ParentalControlPinStorage]
 * for why these exist at all.
 */
interface LockedChannelsStorage {
    suspend fun load(): Set<String>

    suspend fun save(keys: Set<String>)
}

/**
 * The two persisted PIN fields, as [com.uacastplayer.app.ParentalControlController] needs them -
 * implemented for real by [com.uacastplayer.data.prefs.AppPreferences], which already declares
 * exactly these properties.
 *
 * Narrowing the controller's dependency from the whole preferences object to this pair is what
 * makes the lock/unlock rules testable on a plain JVM. Those rules are the point of the feature and
 * are easy to get quietly wrong - that a wrong PIN changes nothing, that unlocking survives for the
 * rest of the session once a correct PIN is entered, that a reset clears the PIN *and* every locked
 * channel - and none of them could be asserted while this depended on SharedPreferences.
 */
interface ParentalControlPinStorage {
    var parentalControlPinHash: String?

    var parentalControlPinSalt: String?
}
