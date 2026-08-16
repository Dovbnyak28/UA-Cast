package com.uacastplayer.data.premium

import com.uacastplayer.log.AppLog
import com.uacastplayer.premium.Entitlements
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseStorage
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.premium.TrialEligibilityPolicy
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
    /**
     * When this install was first put on the device - the one fact clearing app data does not
     * erase - or null where it cannot be read. See [TrialEligibilityPolicy.deservesTrial].
     *
     * Declared before [systemClock] on purpose: every existing caller passes the clock as a
     * trailing lambda, and a new last parameter would have silently taken that binding.
     */
    private val installTime: () -> Long? = { null },
    private val systemClock: () -> Long = System::currentTimeMillis,
) {

    /**
     * The clock every entitlement decision in this class is judged against: the system clock,
     * except that it never goes backwards.
     *
     * Reading it records it, which is what makes the mark advance. See
     * [TrialEligibilityPolicy.clampToHighWaterMark] for why an app that sells time has to do this,
     * and for what it deliberately does not defend against.
     */
    private fun now(): Long {
        val clamped = TrialEligibilityPolicy.clampToHighWaterMark(systemClock(), storage.clockHighWaterMark)
        if (clamped > storage.clockHighWaterMark) storage.clockHighWaterMark = clamped
        return clamped
    }
    private val _entitlements = MutableStateFlow(Entitlements.FREE)
    val entitlements: StateFlow<Entitlements> = _entitlements.asStateFlow()

    private val _connection = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    val connection: StateFlow<BillingConnectionState> = _connection.asStateFlow()

    /**
     * Whether this device has ever been shown something it could actually buy.
     *
     * The gates hang off this, not off the connection state - see `FeatureManager.isUnlocked`. An
     * app that withholds features it has never offered a price for is indistinguishable, from the
     * user's side, from one that is simply broken, and the ways to arrive there do not announce
     * themselves: a product still in draft, an id spelled differently in the console, a release
     * flipped live before the track it is attached to went out. Each of those is answered by Play
     * with a perfectly successful, perfectly empty response.
     *
     * Latched, and persisted, because it must not follow the network. Once the till has been seen
     * open it stays open, or an offline launch would hand the app out for free.
     */
    private val _storeCanSell = MutableStateFlow(storage.storeHasEverOfferedProducts)
    val storeCanSell: StateFlow<Boolean> = _storeCanSell.asStateFlow()

    /** Cancelled and replaced when the store is swapped, so the old provider stops being listened
     * to - otherwise two providers would race to set the license. */
    private var observeJob: Job? = null

    /**
     * Reads the stored license, granting the first-launch trial if this install has never had one,
     * and then starts listening to the store.
     *
     * "Never had one" used to mean "storage is empty", and Android hands the user a button that
     * produces exactly that - Clear data - so the fortnight restarted for anyone willing to spend
     * three taps on it. [TrialEligibilityPolicy.deservesTrial] adds the one fact clearing data does
     * not touch: how old the install itself is. An expired trial still stays stored rather than
     * being cleared, for the same reason as before.
     */
    fun loadInitial() {
        val stored = storage.storedLicense
        val license = stored ?: grantTrialIfDeserved()
        publish(license)
        observeProvider()
    }

    private fun grantTrialIfDeserved(): License {
        if (!TrialEligibilityPolicy.deservesTrial(installTime(), now())) {
            // Not a first launch - an install older than the trial, with its storage emptied. The
            // free tier is what it gets, and it is recorded so this is decided once rather than on
            // every launch from here on.
            AppLog.d(TAG) { "storage was cleared on an install older than the trial: no new trial" }
            return License.FREE.also { storage.storedLicense = it }
        }
        return License.trialStartingAt(now()).also {
            storage.storedLicense = it
            AppLog.d(TAG) { "first launch: granted a trial" }
        }
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
                    if (state == BillingConnectionState.CONNECTED) {
                        noteWhetherAnythingIsForSale()
                        applyPurchases(purchases)
                    }
                }
        }
    }

    /**
     * Asks the store, once, whether it has anything to sell - not on behalf of any screen, but so
     * that the gates have an answer before a user meets one.
     *
     * Deliberately not left to the premium screen to discover. Someone who never opens that screen
     * still runs into the locks, and a check that only happens when the paywall is opened would let
     * a mis-configured catalogue take features away from everyone who never went looking for it.
     */
    private suspend fun noteWhetherAnythingIsForSale() {
        if (_storeCanSell.value) return
        if (provider.products().isEmpty()) {
            AppLog.d(TAG) { "store is connected but sells nothing: gates stay open" }
            return
        }
        storage.storeHasEverOfferedProducts = true
        _storeCanSell.value = true
    }

    private fun applyPurchases(purchases: Set<PurchaseRecord>) {
        // Before the licence is decided, and outside every branch below. Whether a purchase is the
        // one this app grants access from is a different question from whether the store is still
        // waiting to be told it happened, and the early return below would otherwise skip it for a
        // set whose entries have all expired.
        acknowledgeAll(purchases)
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
    private fun acknowledgeAll(purchases: Set<PurchaseRecord>) {
        val owed = purchases.filter { it.needsAcknowledgement }
        if (owed.isEmpty()) return
        scope.launch { owed.forEach { provider.acknowledge(it) } }
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
