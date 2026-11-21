package com.uacastplayer.data.premium

import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.log.AppLog
import com.uacastplayer.premium.Entitlements
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseStorage
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.premium.LicenseClockPolicy
import com.uacastplayer.premium.billing.BillingConnectionState
import com.uacastplayer.premium.billing.BillingProduct
import com.uacastplayer.premium.billing.BillingProvider
import com.uacastplayer.premium.billing.PurchaseRecord
import com.uacastplayer.premium.billing.PurchaseResult
import com.uacastplayer.premium.billing.PremiumProducts
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * from - a real purchase, a stored license read while offline, a legacy paid record, or the debug
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
    private val systemClock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    /**
     * The clock every entitlement decision in this class is judged against: the system clock,
     * except that it never goes backwards.
     *
     * Reading it records it, which is what makes the mark advance. See
     * [LicenseClockPolicy.clampToHighWaterMark] protects expiry on legacy paid records.
     */
    private fun now(): Long {
        val clamped = LicenseClockPolicy.clampToHighWaterMark(systemClock(), storage.clockHighWaterMark)
        if (clamped > storage.clockHighWaterMark) storage.clockHighWaterMark = clamped
        return clamped
    }
    private val _entitlements = MutableStateFlow(Entitlements.FREE)
    val entitlements: StateFlow<Entitlements> = _entitlements.asStateFlow()

    private val _connection = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    val connection: StateFlow<BillingConnectionState> = _connection.asStateFlow()

    /** Cancelled and replaced when the store is swapped, so the old provider stops being listened
     * to - otherwise two providers would race to set the license. */
    private var observeJob: Job? = null
    private var expiryJob: Job? = null
    private var scheduledExpiryLicense: License? = null
    private var scheduledExpiryAt: Long = 0L

    /** A fresh install starts in Lite. Preserve paid records and retire old promotional licences. */
    fun loadInitial() {
        if (closed.get()) return
        val stored = storage.storedLicense
        val license = (stored ?: License.FREE).currentModel()
        if (stored != license) storage.storedLicense = license
        publish(license)
        observeProvider()
    }

    /**
     * Watches the store as a single stream of (connection, purchases) pairs.
     *
     * The two are combined rather than collected separately, and that is not tidiness. Collected
     * apart, the purchases handler has to read the *last seen* connection state, so a provider that
     * reports CONNECTED and a purchase in quick succession can have the purchase evaluated against
     * a connection state that has not arrived yet. Combining observes both latest values without
     * a second mutable copy; a connected transport still needs a non-null ownership answer.
     */
    private fun observeProvider() {
        observeJob?.cancel()
        // One observer belongs to one provider instance. Reading the mutable field again from a
        // suspended collector can otherwise mix an old provider's purchase flow with a replacement
        // provider's catalogue/acknowledgement API during a fast debug-store switch.
        val observedProvider = provider
        observeJob = scope.launch {
            val connected = runCatchingNonFatal { observedProvider.connect() }
                .onFailure { error ->
                    _connection.value = BillingConnectionState.DISCONNECTED
                    AppLog.w(TAG) { "Store connection boundary failed: ${error.javaClass.simpleName}" }
                }
                .isSuccess
            if (!connected || !isCurrentProvider(observedProvider)) return@launch
            combine(observedProvider.connection, observedProvider.purchases) { state, purchases -> state to purchases }
                .collect { (state, purchases) ->
                    if (!isCurrentProvider(observedProvider)) return@collect
                    runCatchingNonFatal {
                        _connection.value = state
                        // Only a *connected* store is allowed to speak about what is owned. An empty set
                        // from a store that is not connected is the absence of an answer, not the answer
                        // "you own nothing" - acting on it would revoke a paid feature offline.
                        if (state == BillingConnectionState.CONNECTED) {
                            // Ownership is independent of the sale catalogue. A slow price query
                            // must not delay an authoritative grant/refund or block this observer.
                            if (purchases != null) applyPurchases(purchases, observedProvider)
                        }
                    }.onFailure { error ->
                        // A single SDK/storage callback must not retire the permanent observer.
                        AppLog.w(TAG) { "Store state boundary failed: ${error.javaClass.simpleName}" }
                    }
                }
        }
    }

    private fun applyPurchases(purchases: Set<PurchaseRecord>, observedProvider: BillingProvider) {
        // Before the licence is decided, and outside every branch below. Whether a purchase is the
        // one this app grants access from is a different question from whether the store is still
        // waiting to be told it happened, and the early return below would otherwise skip it for a
        // set whose entries have all expired.
        acknowledgeAll(purchases, observedProvider)
        val best = bestOf(purchases)
        if (best == null) {
            // The store is connected and says nothing is owned. If the device is holding a paid
            // license, it was cancelled or refunded, and it goes. Lite needs no store ownership.
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
            ).currentModel(),
        )
    }

    /**
     * Every purchase that needs one, not just the one [bestOf] selected.
     *
     * Google Play refunds each unacknowledged purchase on its own three-day clock, and it does not
     * care which of them this app considered best. Acknowledging only the best one therefore
     * reversed a sale whenever a user owned two that both still needed it - an active subscription
     * with a lifetime bought on top, or a pair whose first acknowledgement failed on a bad
     * connection. It fails silently in the worst direction: the user keeps the features, because
     * the licence is decided by the best purchase and that one *was* acknowledged, while the money
     * for the other goes back.
     *
     * There is no retry here beyond the next store update, and none is needed: Play keeps returning
     * a purchase as unacknowledged until it is acknowledged, and `applyPurchases` runs on every
     * connection and every refresh - so a failed attempt is retried on the next launch, well inside
     * three days.
     */
    private fun acknowledgeAll(purchases: Set<PurchaseRecord>, observedProvider: BillingProvider) {
        val owed = purchases.filter { it.needsAcknowledgement }
        if (owed.isEmpty()) return
        scope.launch {
            owed.forEach { purchase ->
                runCatchingNonFatal { observedProvider.acknowledge(purchase) }
                    .onFailure { error ->
                        // Each purchase has its own three-day acknowledgement deadline. One SDK
                        // failure must not prevent the remaining purchases from being attempted.
                        AppLog.w(TAG) { "Purchase acknowledgement failed: ${error.javaClass.simpleName}" }
                    }
            }
        }
    }

    /** Buys [productId]; the resulting entitlement is published through [entitlements] like any
     * other, so the caller does not have to do anything with the result but report it. */
    suspend fun purchase(productId: String, launchContext: Any?): PurchaseResult {
        if (closed.get() || !PremiumProducts.isForSale(productId)) return PurchaseResult.Unavailable
        val activeProvider = provider
        return runCatchingNonFatal {
            val product = offeredProducts(activeProvider).firstOrNull { it.id == productId }
                ?: return@runCatchingNonFatal PurchaseResult.Unavailable
            if (!isCurrentProvider(activeProvider)) return@runCatchingNonFatal PurchaseResult.Unavailable
            val result = activeProvider.purchase(product, launchContext)
            if (!isCurrentProvider(activeProvider)) return@runCatchingNonFatal PurchaseResult.Unavailable
            if (result is PurchaseResult.Success) applyPurchases(setOf(result.purchase), activeProvider)
            result
        }.getOrElse { error ->
            AppLog.w(TAG) { "Purchase boundary failed: ${error.javaClass.simpleName}" }
            PurchaseResult.Failed(reason = null)
        }
    }

    /** "I already paid, on my other phone." Google Play requires every app that sells anything to
     * offer this. */
    suspend fun restore(): PurchaseResult {
        if (closed.get()) return PurchaseResult.Unavailable
        val activeProvider = provider
        return runCatchingNonFatal {
            val result = activeProvider.restore()
            if (isCurrentProvider(activeProvider)) result else PurchaseResult.Unavailable
        }.getOrElse { error ->
            AppLog.w(TAG) { "Restore boundary failed: ${error.javaClass.simpleName}" }
            PurchaseResult.Failed(reason = null)
        }
    }

    /** What can be bought, priced by the store in the user's own currency. */
    suspend fun products(): List<BillingProduct> {
        if (closed.get()) return emptyList()
        val activeProvider = provider
        return runCatchingNonFatal {
            offeredProducts(activeProvider).takeIf { isCurrentProvider(activeProvider) }.orEmpty()
        }.getOrElse { error ->
            AppLog.w(TAG) { "Store catalogue boundary failed: ${error.javaClass.simpleName}" }
            emptyList()
        }
    }

    private suspend fun offeredProducts(activeProvider: BillingProvider): List<BillingProduct> =
        activeProvider.products().filter {
            PremiumProducts.isForSale(it.id) && it.tier == LicenseTier.LIFETIME && it.formattedPrice.isNotBlank()
        }.distinctBy { it.id }

    /**
     * Swaps the store. Used on the day Google Play Billing arrives, and by the debug-only developer
     * menu, whose provider class is not compiled into a release build at all.
     */
    fun useProvider(replacement: BillingProvider) {
        if (replacement === provider) return
        if (closed.get()) {
            closeProvider(replacement)
            return
        }
        val previous = provider
        provider = replacement
        observeJob?.cancel()
        closeProvider(previous)
        _connection.value = BillingConnectionState.DISCONNECTED
        observeProvider()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        observeJob?.cancel()
        observeJob = null
        expiryJob?.cancel()
        expiryJob = null
        scheduledExpiryLicense = null
        closeProvider(provider)
    }

    private fun isCurrentProvider(candidate: BillingProvider): Boolean = !closed.get() && provider === candidate

    private fun closeProvider(candidate: BillingProvider) {
        runCatchingNonFatal { candidate.close() }.onFailure { error ->
            AppLog.w(TAG) { "Store release boundary failed: ${error.javaClass.simpleName}" }
        }
    }

    /** Re-resolves stored access so a time-limited legacy paid record expires on schedule. */
    fun refresh() {
        if (closed.get()) return
        publish(storage.storedLicense ?: License.FREE)
    }

    private fun store(license: License) {
        storage.storedLicense = license
        publish(license)
    }

    private fun publish(license: License) {
        val observedAt = now()
        val resolved = Entitlements.of(license, observedAt)
        _entitlements.value = resolved
        // Refreshing the same record after clock rollback must not restart its elapsed-time timer.
        if (keepLegacyExpiryTimer(resolved, observedAt)) return
        expiryJob?.cancel()
        expiryJob = null
        scheduledExpiryLicense = null
        scheduleLegacyExpiry(resolved, observedAt)
    }

    private fun keepLegacyExpiryTimer(resolved: Entitlements, observedAt: Long): Boolean {
        val unchanged = scheduledExpiryLicense == resolved.license && observedAt <= scheduledExpiryAt
        return unchanged && expiryJob?.isActive == true && !resolved.hasLapsed
    }

    private fun scheduleLegacyExpiry(resolved: Entitlements, observedAt: Long) {
        val deadline = resolved.license.expiresAtMillis ?: return
        if (resolved.hasLapsed) return
        scheduledExpiryLicense = resolved.license
        scheduledExpiryAt = observedAt
        // One timer only for a legacy timed record. It belongs to the ViewModel's scope and is
        // replaced on every ownership change; a previous subscription cannot expire new Premium.
        expiryJob = scope.launch {
            delay(deadline - observedAt)
            runCatchingNonFatal {
                if (storage.storedLicense?.currentModel() == resolved.license) {
                    // Elapsed time reached the original deadline even if the wall clock was wound
                    // back meanwhile. Preserve the paid record, but publish lapsed access.
                    storage.clockHighWaterMark = maxOf(storage.clockHighWaterMark, deadline)
                    refresh()
                }
            }.onFailure { error ->
                AppLog.w(TAG) { "Legacy access expiry boundary failed: ${error.javaClass.simpleName}" }
            }
        }
    }

    /**
     * The most valuable purchase in a set. Someone can hold several at once - a monthly that has not
     * run out beside a lifetime bought yesterday - and the app should honour the best of them.
     * [LicenseTier.LIFETIME] wins outright; between the rest, the one lasting longest wins.
     */
    private fun bestOf(purchases: Set<PurchaseRecord>): PurchaseRecord? {
        val active = purchases.filter {
            it.tier.isPaid && (it.expiresAtMillis == null || it.expiresAtMillis > now())
        }
        return active.firstOrNull { it.tier == LicenseTier.LIFETIME }
            ?: active.maxByOrNull { it.expiresAtMillis ?: Long.MAX_VALUE }
    }
}
