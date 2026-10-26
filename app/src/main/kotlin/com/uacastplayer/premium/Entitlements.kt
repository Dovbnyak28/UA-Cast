package com.uacastplayer.premium

/**
 * A snapshot of what this device can do right now: the license behind it, and the resolved set of
 * unlocked features.
 *
 * The set is computed once, when the license or the clock moves it, rather than evaluated per
 * question. That is what lets the UI treat access as ordinary state it can subscribe to - and it
 * removes a whole class of bug where two screens ask the same question a second apart and get
 * different answers because legacy paid access expired between them.
 */
data class Entitlements(
    val license: License,
    val unlocked: Set<Feature>,
    /** True when legacy paid access has run out; the UI explains the previous purchase history. */
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
        fun of(license: License, nowMillis: Long): Entitlements {
            val current = license.currentModel()
            return Entitlements(
                license = current,
                unlocked = FeaturePolicy.featuresFor(current.effectiveTier(nowMillis)),
                hasLapsed = current.hasLapsed(nowMillis),
            )
        }
    }
}
