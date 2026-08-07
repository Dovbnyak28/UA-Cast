package com.uacastplayer.premium

/**
 * A snapshot of what this device can do right now: the license behind it, and the resolved set of
 * unlocked features.
 *
 * The set is computed once, when the license or the clock moves it, rather than evaluated per
 * question. That is what lets the UI treat access as ordinary state it can subscribe to - and it
 * removes a whole class of bug where two screens ask the same question a second apart and get
 * different answers because a trial expired between them.
 */
data class Entitlements(
    val license: License,
    val unlocked: Set<Feature>,
    /** True when a granted entitlement has run out - the Premium screen says "renew" rather than
     * "upgrade", and the upgrade banner is allowed to appear. */
    val hasLapsed: Boolean = false,
) {

    val effectiveTier: LicenseTier
        get() = if (hasLapsed) LicenseTier.FREE else license.tier

    companion object {
        /** What a device holds before anything has been read from storage or a store - the safe
         * default, since it grants only what an unpaid install gets. */
        val FREE = of(License.FREE, nowMillis = 0L)

        /**
         * Resolves a license against the clock. The single conversion from "what was granted" to
         * "what is unlocked"; both [com.uacastplayer.premium.FeatureManager] and the repository go
         * through it, so an expired license cannot be interpreted two ways.
         */
        fun of(license: License, nowMillis: Long): Entitlements = Entitlements(
            license = license,
            unlocked = FeaturePolicy.featuresFor(license.effectiveTier(nowMillis)),
            hasLapsed = license.hasLapsed(nowMillis),
        )
    }
}
