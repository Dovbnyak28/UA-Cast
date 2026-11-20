package com.uacastplayer.premium.billing

import com.uacastplayer.premium.LicenseTier

/** Whether the store can be reached. [UNAVAILABLE] is not an error to show anyone: an APK
 * sideloaded onto a device with no Play Services is a supported way to run this app. */
enum class BillingConnectionState { DISCONNECTED, CONNECTING, CONNECTED, UNAVAILABLE }

/**
 * Something that can be bought, as the store describes it.
 *
 * [formattedPrice] comes from the store and is never built in the app. Google Play returns the
 * price in the user's own currency, with regional pricing and any running promotion already
 * applied - an app that renders a hardcoded "$4.99" is simply lying to most of the world.
 */
data class BillingProduct(
    val id: String,
    val tier: LicenseTier,
    val title: String,
    val formattedPrice: String,
)

/** A purchase the user holds according to the store. */
data class PurchaseRecord(
    val productId: String,
    val tier: LicenseTier,
    val purchasedAtMillis: Long,
    /** When this stops applying; null for a non-expiring purchase. */
    val expiresAtMillis: Long?,
    /**
     * Google Play requires a purchase to be acknowledged within three days or it is refunded
     * automatically. Carried here so the repository can tell a provider to finish the job rather
     * than the app silently losing a sale it already made.
     */
    val needsAcknowledgement: Boolean = false,
)

/** How a purchase or a restore ended. Cancellation is separated from failure because the user
 * closing the store sheet is not an error and must not produce an error message. */
sealed interface PurchaseResult {
    data class Success(val purchase: PurchaseRecord) : PurchaseResult
    data object Cancelled : PurchaseResult
    data object AlreadyOwned : PurchaseResult

    /**
     * A restore that reached the store and found nothing to restore.
     *
     * Separate from [Unavailable] because the two need opposite messages and only one of them is a
     * problem: "you have not bought this on this account" is an answer, and telling that user the
     * store could not be reached sends them to check their connection over and over.
     */
    data object NothingToRestore : PurchaseResult

    data object Unavailable : PurchaseResult
    data class Failed(val reason: String?) : PurchaseResult
}
