package com.uacastplayer.data.premium

import com.uacastplayer.log.AppLog
import com.uacastplayer.premium.Entitlements
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseStorage
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.premium.billing.BillingConnectionState
import com.uacastplayer.premium.billing.BillingProvider
import com.uacastplayer.premium.billing.PurchaseRecord
import com.uacastplayer.premium.billing.PurchaseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val TAG = "PremiumRepository"

/**
 * The only thing in this app that talks to a [BillingProvider], and the only thing that decides what
 * license the device currently holds.
 *
 * Everything above it sees one [StateFlow] of [Entitlements] and cannot tell where the answer came
 * from - a real purchase, a stored license read while offline, the first-launch trial, or the debug
 * menu. That indifference is the point: it is what lets the store be swapped without a single screen
 * changing.
 *
 * **The rule that matters here is which way it fails.** A store that cannot be reached does not mean
 * "has not paid". If it did, a paying user would lose everything they bought the moment their phone
 * lost signal. So the stored license stands until the store contradicts it, and a cancellation is
 * noticed on the next successful connection instead. Losing a day of already-paid access is a small
 * wrong; confiscating paid features on an aeroplane is a large one.
 */
class PremiumRepository(
    private var provider: BillingProvider,
    private val storage: LicenseStorage,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _entitlements = MutableStateFlow(Entitlements.FREE)
    val entitlements: StateFlow<Entitlements> = _entitlements.asStateFlow()

    private val _connection = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    val connection: StateFlow<BillingConnectionState> = _connection.asStateFlow()

    /** Cancelled and replaced when the store is swapped, so the old provider stops being listened
     * to - otherwise two providers would race to set the license. */
    private var observeJob: Job? = null

    /**
     * Reads the stored license, granting the first-launch trial if there has never been one, and
     * then starts listening to the store.
     *
     * The trial is granted exactly once, because "never held a license" is recorded the moment it is
     * granted. An expired trial stays stored rather than being cleared - clearing it would hand the
     * same device a fresh 14 days on the next launch, forever.
     */
    fun loadInitial() {
        val stored = storage.storedLicense
        val license = if (stored == null) {
            License.trialStartingAt(now()).also {
                storage.storedLicense = it
                AppLog.d(TAG) { "first launch: granted a trial" }
            }
        } else {
            stored
        }
        publish(license)
        observeProvider()
    }

    /**
     * Watches the store as a single stream of (connection, purchases) pairs.
     *
     * The two are combined rather than collected separately, and that is not tidiness. Collected
     * apart, the purchases handler has to read the *last seen* connection state, so a provider that
     * reports CONNECTED and a purchase in quick succession can have the purchase evaluated against
     * a connection state that has not arrived yet - and a real purchase is then silently dropped
     * because the store "was not connected". Combining makes the pair consistent by construction.
     */
    private fun observeProvider() {
        observeJob?.cancel()
        observeJob = scope.launch {
            provider.connect()
            combine(provider.connection, provider.purchases) { state, purchases -> state to purchases }
                .collect { (state, purchases) ->
                    _connection.value = state
                    // Only a *connected* store is allowed to speak about what is owned. An empty set
                    // from a store that is not connected is the absence of an answer, not the answer
                    // "you own nothing" - acting on it would revoke a paid feature offline.
                    if (state == BillingConnectionState.CONNECTED) applyPurchases(purchases)
                }
        }
    }

    private fun applyPurchases(purchases: Set<PurchaseRecord>) {
        val best = bestOf(purchases)
        if (best == null) {
            // The store is connected and says nothing is owned. If the device is holding a paid
            // license, it was cancelled or refunded, and it goes; a running trial is untouched,
            // since no store ever issued it.
            val stored = storage.storedLicense
            if (stored != null && stored.tier.isPaid) {
                AppLog.d(TAG) { "store reports no purchases: clearing a paid license" }
                store(License.FREE)
            }
            return
        }

        store(
            License(
                tier = best.tier,
                expiresAtMillis = best.expiresAtMillis,
                source = best.productId,
            ),
        )

        if (best.needsAcknowledgement) {
            // Google Play refunds anything unacknowledged after three days. Skipping this does not
            // fail loudly - it quietly reverses a sale that already happened.
            scope.launch { provider.acknowledge(best) }
        }
    }

    /** Buys [productId]; the resulting entitlement is published through [entitlements] like any
     * other, so the caller does not have to do anything with the result but report it. */
    suspend fun purchase(productId: String, launchContext: Any?): PurchaseResult {
        val product = provider.products().firstOrNull { it.id == productId }
            ?: return PurchaseResult.Unavailable
        return provider.purchase(product, launchContext).also { result ->
            if (result is PurchaseResult.Success) applyPurchases(setOf(result.purchase))
        }
    }

    /** "I already paid, on my other phone." Google Play requires every app that sells anything to
     * offer this. */
    suspend fun restore(): PurchaseResult = provider.restore()

    /** What can be bought, priced by the store in the user's own currency. */
    suspend fun products() = provider.products()

    /**
     * Swaps the store. Used on the day Google Play Billing arrives, and by the debug-only developer
     * menu, whose provider class is not compiled into a release build at all.
     */
    fun useProvider(replacement: BillingProvider) {
        provider = replacement
        _connection.value = BillingConnectionState.DISCONNECTED
        observeProvider()
    }

    /** Re-resolves the stored license against the current clock - the trial has to actually end at
     * some point, and nothing else would notice that it had. */
    fun refresh() {
        publish(storage.storedLicense ?: License.FREE)
    }

    private fun store(license: License) {
        storage.storedLicense = license
        publish(license)
    }

    private fun publish(license: License) {
        _entitlements.value = Entitlements.of(license, now())
    }

    /**
     * The most valuable purchase in a set. Someone can hold several at once - a monthly that has not
     * run out beside a lifetime bought yesterday - and the app should honour the best of them.
     * [LicenseTier.LIFETIME] wins outright; between the rest, the one lasting longest wins.
     */
    private fun bestOf(purchases: Set<PurchaseRecord>): PurchaseRecord? {
        val active = purchases.filter { it.expiresAtMillis == null || it.expiresAtMillis > now() }
        return active.firstOrNull { it.tier == LicenseTier.LIFETIME }
            ?: active.maxByOrNull { it.expiresAtMillis ?: Long.MAX_VALUE }
    }
}
