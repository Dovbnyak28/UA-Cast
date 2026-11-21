package com.uacastplayer.premium

/**
 * The single place in this project that knows what is sold.
 *
 * Everything else asks [FeatureManager] a yes/no question about one [Feature]; only this object
 * knows which tier answers yes. Changing the business model - moving a feature between free and
 * paid, adding a tier that unlocks less than the others - means editing the table below and its
 * test, and nothing else. That property is the entire reason the premium layer is worth having:
 * with `if (isPremium)` scattered across screens, the same change would be a search-and-replace
 * through the UI.
 */
object FeaturePolicy {

    /**
     * What an unpaid install can do, and it is deliberately most of the app: one playlist, local
     * playback, the TV guide, favorites, all three themes, Chromecast and picture-in-picture.
     *
     * The reasoning is that people pay for an app they already like. Withholding the parts that
     * make it a working player only guarantees it is uninstalled before anyone forms an opinion -
     * and casting is the thing most users will try in their first ten minutes.
     */
    private val FREE_FEATURES: Set<Feature> = setOf(
        Feature.CHROMECAST,
        // Free because [Feature.CHROMECAST] is. Remuxing is not a capability anyone would buy - it
        // is the fallback that makes casting work when a receiver cannot play a stream directly,
        // and it engages by itself, deep in the cast path, with no user action to put a paywall in
        // front of. Selling it would have meant a free user's casting quietly failing on some
        // channels and working on others, with nothing on screen connecting that to a price. It
        // was listed as sold and gated nowhere, which is how that nearly shipped.
        Feature.RAW_TS_REMUX,
        Feature.PIP,
        Feature.THEMES,
    )

    /**
     * Features for a given tier.
     *
     * Lite keeps the basic player features. Premium includes every capability; existing paid
     * subscriptions are accepted for restoration, while old trials/tester tiers grant no extra access.
     */
    fun featuresFor(tier: LicenseTier): Set<Feature> =
        if (tier.isPaid) Feature.entries.toSet() else FREE_FEATURES

    /** Whether [feature] is one an unpaid install already has - used by the UI to decide whether a
     * lock badge belongs next to something, without it having to know the tier table. */
    fun isFree(feature: Feature): Boolean = feature in FREE_FEATURES
}
