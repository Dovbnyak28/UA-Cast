package com.uacastplayer.premium

/**
 * What the user holds, and until when.
 *
 * The expiry is kept beside the tier rather than folded into it, because an expired legacy purchase is
 * not the same thing as [LicenseTier.FREE] even though both unlock the same features: one of them
 * has a person behind it who paid once and should be told their previous access lapsed, not silently
 * shown fewer buttons.
 *
 * @param tier what was granted.
 * @param expiresAtMillis wall clock at which it stops applying; null means it never does
 *   ([LicenseTier.FREE] and [LicenseTier.LIFETIME]).
 * @param source where this came from - a store product id, `trial`, or the developer menu. Carried
 *   for diagnostics and for the Premium screen's "restore purchases" copy; nothing branches on it.
 */
data class License(
    val tier: LicenseTier,
    val expiresAtMillis: Long? = null,
    val source: String? = null,
) {

    /** Preserve existing paid rights/expiry while retiring all unpaid promotional tiers. Raw tier
     * names remain readable so migration happens after the original record's MAC is verified. */
    fun currentModel(): License = when {
        tier == LicenseTier.LIFETIME -> this
        tier.isPaid -> copy(tier = LicenseTier.LIFETIME)
        else -> FREE
    }

    /**
     * Whether this license still applies at [nowMillis].
     *
     * [LicenseTier.FREE] is always active - "free has expired" is not a state anything can be in.
     */
    fun isActive(nowMillis: Long): Boolean {
        if (tier == LicenseTier.FREE) return true
        return expiresAtMillis == null || nowMillis < expiresAtMillis
    }

    /**
     * The tier that actually governs access right now: the granted one while it lasts, and
     * [LicenseTier.FREE] once it does not.
     *
     * Everything that decides what is unlocked goes through here rather than reading [tier], so an
     * expired license cannot unlock anything by being read in one place that forgot to check the
     * date.
     */
    fun effectiveTier(nowMillis: Long): LicenseTier =
        if (isActive(nowMillis)) tier else LicenseTier.FREE

    /** True once a granted, time-limited entitlement has run out - the one case worth its own
     * message on screen, rather than a silently smaller app. */
    fun hasLapsed(nowMillis: Long): Boolean = tier != LicenseTier.FREE && !isActive(nowMillis)

    companion object {
        /** What a device holds before anything else has happened. */
        val FREE = License(LicenseTier.FREE)

    }
}
