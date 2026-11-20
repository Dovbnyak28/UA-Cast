package com.uacastplayer.premium

/** Selects the checkout provider. Lite/Premium access is resolved from the licence independently
 * of this flag. Enable real billing only after the one-time premium_lifetime product is activated
 * and verified on a Play test track; see docs/RELEASING.md. */
object PremiumAvailability {
    const val STORE_IS_LIVE = false
}
