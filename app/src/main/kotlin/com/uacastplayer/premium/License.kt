package com.uacastplayer.premium

/**
 * What the user holds, and until when.
 *
 * The expiry is kept beside the tier rather than folded into it, because an expired [MONTHLY] is
 * not the same thing as [LicenseTier.FREE] even though both unlock the same features: one of them
 * has a person behind it who paid once and should be told their subscription lapsed, not silently
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

        /** Length of the automatic first-launch trial. */
        const val TRIAL_DURATION_MILLIS: Long = 14L * 24 * 60 * 60 * 1000

        /**
         * The trial a fresh install is granted, ending [TRIAL_DURATION_MILLIS] after [nowMillis].
         *
         * Granted once and stored, rather than recomputed from an install date: a stored end date
         * is a fact, while "installed 14 days ago" becomes wrong the moment the clock is touched.
         */
        fun trialStartingAt(nowMillis: Long): License = License(
            tier = LicenseTier.TRIAL,
            expiresAtMillis = nowMillis + TRIAL_DURATION_MILLIS,
            source = "trial",
        )
    }
}
