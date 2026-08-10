package com.uacastplayer.premium

/**
 * Whether there is anything to sell yet.
 *
 * There is not. [com.uacastplayer.data.premium.FakeBillingProvider] reports, truthfully, that this
 * build has no store behind it - so every price is absent, every purchase button would do nothing,
 * and the "upgrade" the section offers leads nowhere. Showing that to a user is worse than showing
 * nothing: it is a promise the app cannot keep, on the one screen where it is asking to be trusted
 * with money.
 *
 * A `const val`, deliberately. R8 folds the branch and strips the unreachable surface out of a
 * release build entirely, so this is not a runtime check that could be flipped by accident - it is
 * the difference between shipping the screen and not shipping it.
 *
 * **What this does not touch.** [FeaturePolicy], [FeatureManager], [Entitlements] and the license
 * storage all stay exactly as they are, and the gates in front of DLNA, extra playlists, Xtream,
 * backups, parental control, custom EPG and custom icon sources are already wired. They simply do
 * not withhold anything while this is false - see [FeatureManager]'s `withholdingIsHonest`, which
 * refuses to lock a feature the build has no way of selling.
 *
 * **Flipping it to true is not the last step, and is not sufficient on its own.** Play Console must
 * first have in-app products under exactly the ids in
 * [com.uacastplayer.premium.billing.PremiumProducts], on a track that has actually gone out. If it
 * does not, the store answers with an empty catalogue and the gates stay open anyway - deliberately,
 * because an app that takes features away and offers nothing to buy them back with is worse than one
 * that never gated them. `docs/RELEASING.md` has the checklist.
 */
object PremiumAvailability {

    const val STORE_IS_LIVE = false
}
