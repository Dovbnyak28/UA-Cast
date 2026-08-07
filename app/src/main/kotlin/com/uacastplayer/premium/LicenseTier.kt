package com.uacastplayer.premium

/**
 * What kind of entitlement a user holds. Deliberately *not* a boolean: the difference between
 * "never paid", "trial ran out" and "subscription lapsed" is the difference between three very
 * different things to say on screen, and a boolean throws all three away.
 */
enum class LicenseTier {
    /** Never paid, or paid and it has since expired. The only tier that cannot expire. */
    FREE,

    /** Time-limited full access granted automatically on first launch. */
    TRIAL,

    MONTHLY,
    YEARLY,

    /** Bought once, never expires. */
    LIFETIME,

    /** Granted outside the store to testers. Expires, so a forgotten tester does not hold full
     * access forever. */
    BETA,

    /** Developer access. Never issued by a store - only by the debug-only provider, which is not
     * compiled into a release build at all (see the `src/debug` source set). */
    ADMIN,
    ;

    /** Whether this tier is a paid one, for copy that has to distinguish "upgrade" from "renew". */
    val isPaid: Boolean
        get() = this == MONTHLY || this == YEARLY || this == LIFETIME
}
