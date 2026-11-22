package app.zorik.player.premium

/** Selects the checkout provider. Lite/Premium access is resolved from the licence independently
 * of this flag.
 *
 * Live only in the `play` build type (see `PLAY_BILLING_LIVE` in app/build.gradle.kts): Google
 * Play can take payment only from a build it installed, so the sideloaded GitHub APK and debug
 * builds keep [app.zorik.player.data.premium.FakeBillingProvider]. It is a compile-time constant,
 * so R8 drops the unused provider from each variant. The `premium_lifetime` product still has to
 * be active in Play Console and verified by a licence tester on a test track before a production
 * rollout; see docs/RELEASING.md. */
object PremiumAvailability {
    const val STORE_IS_LIVE = app.zorik.player.BuildConfig.PLAY_BILLING_LIVE
}
