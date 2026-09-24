package com.uacastplayer.update

/**
 * The values the update check has to remember between launches, as an interface so
 * [com.uacastplayer.app.UpdateController] can be tested without a `Context` - the same shape
 * [com.uacastplayer.parentalcontrol.ParentalControlPinStorage] uses.
 */
interface UpdateCheckStorage {

    /** Wall clock of the last check of either kind; null means there has never been one. */
    var lastUpdateCheckAtMillis: Long?

    /** A failed network check retries sooner than a successful one. */
    var lastUpdateCheckFailed: Boolean

    /** Release tag whose banner the user closed, so it stays closed for that version only. */
    var dismissedUpdateTag: String?

    /** Release whose install invitation was last shown. */
    var promptedUpdateTag: String?

    /** Wall-clock time of that invitation, used to schedule a later reminder. */
    var lastUpdatePromptAtMillis: Long?
}
