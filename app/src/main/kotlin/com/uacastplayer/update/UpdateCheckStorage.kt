package com.uacastplayer.update

/**
 * The two values the update check has to remember between launches, as an interface so
 * [com.uacastplayer.app.UpdateController] can be tested without a `Context` - the same shape
 * [com.uacastplayer.parentalcontrol.ParentalControlPinStorage] uses.
 */
interface UpdateCheckStorage {

    /** Wall clock of the last check of either kind; null means there has never been one. */
    var lastUpdateCheckAtMillis: Long?

    /** Release tag whose banner the user closed, so it stays closed for that version only. */
    var dismissedUpdateTag: String?
}
