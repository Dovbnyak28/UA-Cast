package com.uacastplayer.premium.billing

import kotlinx.coroutines.flow.StateFlow

/**
 * A store, whichever one it is. The only place in this project that is allowed to know about Google
 * Play - and it does not know either, it just declares the shape any store has to fit.
 *
 * The point of this interface is a specific, testable claim: on the day this app is published,
 * turning on real purchases means writing one implementation and changing one line where the
 * provider is constructed. `FeatureManager`, `PremiumRepository` and every screen stay closed.
 *
 * Implementations planned or possible: the fake used until publication, a debug-only one driving
 * every license state by hand, Google Play, Amazon, Huawei, and a server-issued license for
 * distribution outside any store.
 */
interface BillingProvider {

    /** Whether the store can be reached. Drives nothing but copy - access itself falls back to the
     * cached license, so a disconnected store never removes a paid feature. */
    val connection: StateFlow<BillingConnectionState>

    /**
     * What the user owns according to the store.
     *
     * Empty means "the store says nothing is owned", which is *not* the same as "unknown" - see
     * [connection]. A provider that cannot reach its store reports [BillingConnectionState.DISCONNECTED]
     * and leaves this at its last known value rather than clearing it, because clearing it would
     * read as a refund.
     */
    val purchases: StateFlow<Set<PurchaseRecord>>

    /** Opens the connection and starts populating [purchases]. Safe to call more than once. */
    suspend fun connect()

    /** What can be bought, with prices as the store formats them. Empty when the store is
     * unreachable - the Premium screen then offers "restore" and nothing to buy. */
    suspend fun products(): List<BillingProduct>

    /**
     * Runs the store's purchase flow.
     *
     * @param launchContext the host the store needs to show its own UI over. Typed as [Any] on
     *   purpose: Google Play needs an `android.app.Activity`, and naming that type here would drag
     *   the Android SDK into a layer whose whole value is not depending on it. A provider that does
     *   not need a host ignores it.
     */
    suspend fun purchase(product: BillingProduct, launchContext: Any?): PurchaseResult

    /** Re-reads entitlements from the store - the "I already paid, on my other phone" path, which
     * Google Play requires every app selling anything to offer. */
    suspend fun restore(): PurchaseResult

    /**
     * Confirms a purchase to the store. Google Play automatically refunds anything not acknowledged
     * within three days, so this is not optional bookkeeping - skipping it silently reverses sales.
     */
    suspend fun acknowledge(purchase: PurchaseRecord)
}
