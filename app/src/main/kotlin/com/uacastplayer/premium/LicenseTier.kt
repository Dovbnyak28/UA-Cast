package com.uacastplayer.premium

/** Lite and one-time Premium use FREE and LIFETIME. Other values decode older signed records
 * and restore existing purchases only; they are never offered or granted by the current app. */
enum class LicenseTier {
    /** Lite: never paid, or previous paid access has expired. Lite itself never expires. */
    FREE,

    /** Legacy automatic trial; resolves to Lite in the current model. */
    TRIAL,

    MONTHLY,
    YEARLY,

    /** Current one-time Premium. A migrated legacy record may retain its original expiry. */
    LIFETIME,

    /** Legacy tester record; resolves to Lite in the current model. */
    BETA,

    /** Legacy developer record; current debug testing uses the same Premium tier as production. */
    ADMIN,
    ;

    /** Whether this tier is a paid one, for copy that has to distinguish "upgrade" from "renew". */
    val isPaid: Boolean
        get() = this == MONTHLY || this == YEARLY || this == LIFETIME
}
