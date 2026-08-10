package com.uacastplayer.premium.billing

import com.uacastplayer.premium.LicenseTier

/**
 * The product ids this app sells, and what each one grants.
 *
 * **These strings must match the in-app products created in Play Console exactly.** Play has no
 * concept of a typo here: a product id the console does not know is simply absent from the query
 * response, so the price never appears and the buy button never works - with no error anywhere,
 * because as far as Play is concerned the app asked about something that does not exist. That is
 * the single most likely way for a launch to go quiet, which is why the ids live in one named
 * place rather than inline at the call site.
 *
 * The type matters as much as the id. Play treats subscriptions and one-time purchases as separate
 * catalogues, queried separately and owned separately, so [LIFETIME] cannot be created as a
 * subscription in the console without this table becoming a lie.
 */
object PremiumProducts {

    /** Play's own product-type constants, repeated here so this file does not have to import the
     * billing library - it is the one part of the store layer that is pure data and testable
     * without Android. Asserted against `BillingClient.ProductType` in `PremiumProductsTest`. */
    const val TYPE_SUBSCRIPTION = "subs"
    const val TYPE_ONE_TIME = "inapp"

    const val MONTHLY = "premium_monthly"
    const val YEARLY = "premium_yearly"
    const val LIFETIME = "premium_lifetime"

    /** Every id, with its Play product type - what [BillingProvider.products] has to ask for. */
    val ALL: Map<String, String> = mapOf(
        MONTHLY to TYPE_SUBSCRIPTION,
        YEARLY to TYPE_SUBSCRIPTION,
        LIFETIME to TYPE_ONE_TIME,
    )

    val SUBSCRIPTION_IDS: List<String> = ALL.filterValues { it == TYPE_SUBSCRIPTION }.keys.toList()
    val ONE_TIME_IDS: List<String> = ALL.filterValues { it == TYPE_ONE_TIME }.keys.toList()

    /**
     * What owning [productId] entitles the user to, or null for an id this build does not sell.
     *
     * Null rather than a default tier on purpose: a purchase of something unrecognised must not
     * quietly unlock the app. That happens for real - a product renamed in the console, an old id
     * still owned by a long-time user, a purchase made against a different build - and guessing
     * would mean either giving away the app or revoking something someone paid for. The caller
     * decides; it does not get a silent answer here.
     */
    fun tierFor(productId: String): LicenseTier? = when (productId) {
        MONTHLY -> LicenseTier.MONTHLY
        YEARLY -> LicenseTier.YEARLY
        LIFETIME -> LicenseTier.LIFETIME
        else -> null
    }
}
