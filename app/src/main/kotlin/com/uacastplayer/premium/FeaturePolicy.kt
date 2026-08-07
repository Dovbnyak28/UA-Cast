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
        Feature.PIP,
        Feature.THEMES,
    )

    /**
     * Features for a given tier.
     *
     * Every tier above [LicenseTier.FREE] currently unlocks everything, including the trial - a
     * trial that hides what is being sold does not sell it.
     *
     * When a server-backed feature actually ships, this is where it stops being uniform:
     * [Feature.CLOUD_SYNC] costs money for as long as it runs, so a one-off [LicenseTier.LIFETIME]
     * funding it forever is a trap worth pricing deliberately rather than inheriting from a table
     * that says "everything".
     */
    fun featuresFor(tier: LicenseTier): Set<Feature> = when (tier) {
        LicenseTier.FREE -> FREE_FEATURES
        LicenseTier.TRIAL,
        LicenseTier.MONTHLY,
        LicenseTier.YEARLY,
        LicenseTier.LIFETIME,
        LicenseTier.BETA,
        LicenseTier.ADMIN,
        -> Feature.entries.toSet()
    }

    /** Whether [feature] is one an unpaid install already has - used by the UI to decide whether a
     * lock badge belongs next to something, without it having to know the tier table. */
    fun isFree(feature: Feature): Boolean = feature in FREE_FEATURES
}
