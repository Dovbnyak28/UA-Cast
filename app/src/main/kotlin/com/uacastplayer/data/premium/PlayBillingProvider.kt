package com.uacastplayer.data.premium

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.uacastplayer.log.AppLog
import com.uacastplayer.premium.billing.BillingConnectionState
import com.uacastplayer.premium.billing.BillingProduct
import com.uacastplayer.premium.billing.BillingProvider
import com.uacastplayer.premium.billing.PremiumProducts
import com.uacastplayer.premium.billing.PurchaseRecord
import com.uacastplayer.premium.billing.PurchaseResult
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "PlayBilling"

/**
 * Google Play, behind [BillingProvider].
 *
 * This is the implementation the interface was written for: everything above it - `FeatureManager`,
 * `PremiumRepository`, every screen - is unchanged by its arrival, and swapping it back out for
 * [FakeBillingProvider] is one line in `AppViewModel`.
 *
 * **What this file cannot promise.** Nothing here has been exercised against a real store. Play
 * Billing only answers an app that is uploaded to a Play track, signed with the key Play expects,
 * and installed from Play by an account on the licence-tester list - none of which can be arranged
 * from a development machine. What *is* verified is that it compiles against Billing 8.0.0 and that
 * the mapping between Play's vocabulary and this app's is covered by tests. The first real purchase
 * is the first real test, and it should be made by a licence tester, not a customer.
 *
 * **Expiry is Play's business, not ours.** [PurchaseRecord.expiresAtMillis] stays null for
 * subscriptions here, because a client cannot know when one lapses - Play's own answer is simply to
 * stop returning it from `queryPurchasesAsync`. So a lapsed subscription arrives as an empty
 * purchase set, which the repository already reads as "no longer entitled". Inventing an expiry date
 * from `purchaseTime` plus a month would be a guess that revokes access on the wrong day.
 */
@Suppress("ReturnCount")
class PlayBillingProvider(
    context: Context,
    /** Where work Play asks for out of band runs - a reconnection is announced on Play's schedule,
     * not inside any call this app made, so there is no caller's coroutine to borrow. */
    private val scope: CoroutineScope,
) : BillingProvider {

    // On the suppression above. Most of this class translates Play's vocabulary into this app's,
    // and every translation has several ways of meaning "nothing to report": a pending purchase, a
    // product id this build does not sell, an offer with no price, a store that is not ready. Each
    // is a guard clause - `?: return null` - and each carries a comment saying which case it is.
    // Folding four of those into one nested expression to satisfy a return counter would make the
    // one thing worth reading here, namely which inputs are rejected and why, harder to see.

    private val appContext = context.applicationContext

    private val _connection = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    override val connection: StateFlow<BillingConnectionState> = _connection.asStateFlow()

    private val _purchases = MutableStateFlow<Set<PurchaseRecord>>(emptySet())
    override val purchases: StateFlow<Set<PurchaseRecord>> = _purchases.asStateFlow()

    /**
     * Play's own objects, kept because its API needs them back.
     *
     * [ProductDetails] is required by `launchBillingFlow` and cannot be rebuilt from a
     * [BillingProduct]; the purchase token is required to acknowledge a purchase and is deliberately
     * absent from [PurchaseRecord], which exists so that no store's identifiers leak into the rest
     * of the app.
     */
    private val productDetails = ConcurrentHashMap<String, ProductDetails>()
    private val purchaseTokens = ConcurrentHashMap<String, String>()

    /** Completed by [purchasesUpdatedListener]; Play reports the outcome of a purchase through the
     * client-wide listener rather than through the call that started it. */
    @Volatile private var pendingPurchase: CompletableDeferred<PurchaseResult>? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        val records = purchases.orEmpty().mapNotNull(::toRecord)
        if (records.isNotEmpty()) {
            _purchases.value = _purchases.value + records
        }
        pendingPurchase?.complete(outcomeOf(result, records.firstOrNull()))
        pendingPurchase = null
    }

    private fun onReconnected() {
        scope.launch { refreshPurchases() }
    }

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        // Required from Billing 6; without it the client refuses to build. One-time products only -
        // this app sells no prepaid subscription plans.
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        // Play kills the service on its own schedule (updates, low memory, doze). Without this the
        // first call after that is a hard failure the user sees as "the store is unavailable".
        .enableAutoServiceReconnection()
        .build()

    override suspend fun connect() {
        if (client.isReady) return
        _connection.value = BillingConnectionState.CONNECTING
        val result = suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                @Volatile private var resumed = false

                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    // Called again on every automatic reconnection, not only for the call that
                    // started this one - which is why the state is published here rather than only
                    // from the result below. Play restarts its service on its own schedule, and
                    // without this the flow would stay DISCONNECTED for the rest of the process:
                    // the repository only acts on a *connected* store, so a cancellation would
                    // never be noticed and a purchase made on another device never picked up.
                    _connection.value = stateFor(billingResult.responseCode)
                    if (resumed) {
                        // A reconnection rather than the first connection. What the user owns may
                        // have changed while the service was away, and nothing else will ask.
                        if (_connection.value == BillingConnectionState.CONNECTED) onReconnected()
                        return
                    }
                    resumed = true
                    continuation.resume(billingResult.responseCode)
                }

                override fun onBillingServiceDisconnected() {
                    _connection.value = BillingConnectionState.DISCONNECTED
                    if (!resumed) {
                        resumed = true
                        continuation.resume(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                    }
                }
            })
        }
        _connection.value = stateFor(result)
        AppLog.d(TAG) { "connection: ${_connection.value}" }
        if (_connection.value == BillingConnectionState.CONNECTED) refreshPurchases()
    }

    private fun stateFor(responseCode: Int): BillingConnectionState = when (responseCode) {
        BillingClient.BillingResponseCode.OK -> BillingConnectionState.CONNECTED
        // A device with no Play Services at all - a sideloaded APK on a TV box, which this app
        // supports. Not an error, and nothing is shown to anyone.
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
        -> BillingConnectionState.UNAVAILABLE
        else -> BillingConnectionState.DISCONNECTED
    }

    override suspend fun products(): List<BillingProduct> {
        if (!client.isReady) return emptyList()
        return query(PremiumProducts.SUBSCRIPTION_IDS, PremiumProducts.TYPE_SUBSCRIPTION) +
            query(PremiumProducts.ONE_TIME_IDS, PremiumProducts.TYPE_ONE_TIME)
    }

    private suspend fun query(ids: List<String>, type: String): List<BillingProduct> {
        if (ids.isEmpty()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ids.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(type)
                        .build()
                },
            )
            .build()
        val response = client.queryProductDetails(params)
        if (response.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            AppLog.w(TAG) { "product query failed: ${response.billingResult.responseCode}" }
            return emptyList()
        }
        val details = response.productDetailsList.orEmpty()
        // An id the console does not know is silently missing rather than reported - the single
        // most common launch failure, so it is logged as the difference between asked and answered.
        if (details.size != ids.size) {
            AppLog.w(TAG) { "store knows ${details.size} of ${ids.size} $type product(s)" }
        }
        details.forEach { productDetails[it.productId] = it }
        return details.mapNotNull(::toProduct)
    }

    private fun toProduct(details: ProductDetails): BillingProduct? {
        val tier = PremiumProducts.tierFor(details.productId) ?: return null
        val price = details.oneTimePurchaseOfferDetails?.formattedPrice
            // The *last* pricing phase, not the first. A base plan with an introductory offer -
            // a free first month, a discounted first year, both of which Play pushes hard - arrives
            // as a list of phases ending in the one that repeats forever. Taking the first would
            // print "0,00 ₴" as the price of a monthly subscription: technically what they pay
            // today, and a lie about what they are agreeing to. Never formatted here - Play has
            // already applied the user's currency and regional pricing.
            ?: details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.lastOrNull()
                ?.formattedPrice
            ?: return null
        return BillingProduct(id = details.productId, tier = tier, title = details.title, formattedPrice = price)
    }

    override suspend fun purchase(product: BillingProduct, launchContext: Any?): PurchaseResult {
        val activity = launchContext as? Activity ?: return PurchaseResult.Failed("no activity")
        val details = productDetails[product.id] ?: return PurchaseResult.Unavailable
        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        // Subscriptions are bought as a specific offer; one-time products have none and setting a
        // token on them is rejected.
        details.subscriptionOfferDetails?.firstOrNull()?.offerToken?.let(paramsBuilder::setOfferToken)
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()

        val deferred = CompletableDeferred<PurchaseResult>()
        // The one being replaced is answered before it is dropped. Play reports a purchase through
        // the client-wide listener rather than through the call that started it, so there is exactly
        // one slot: a second attempt overwriting the first left that first coroutine awaiting a
        // reply now addressed to the second, and it would never arrive. The UI is what stops two
        // attempts from overlapping (see AppViewModel.isPurchasing); this is what stops a
        // coroutine being stranded if one ever does.
        pendingPurchase?.complete(PurchaseResult.Cancelled)
        pendingPurchase = deferred
        val launch = client.launchBillingFlow(activity, flow)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchase = null
            return outcomeOf(launch, null)
        }
        return deferred.await()
    }

    override suspend fun restore(): PurchaseResult {
        if (!client.isReady) return PurchaseResult.Unavailable
        val records = refreshPurchases() ?: return PurchaseResult.Unavailable
        // Reached the store, and this account owns nothing. That is an answer, not a failure, and
        // the user needs to be told which of the two it was.
        return records.firstOrNull()?.let(PurchaseResult::Success) ?: PurchaseResult.NothingToRestore
    }

    /**
     * Both catalogues, because Play keeps subscriptions and one-time purchases apart - or null if
     * either query failed.
     *
     * **Null rather than an empty set, and this is the difference that matters.** The repository
     * reads an empty set from a connected store as "cancelled or refunded" and clears a paid
     * license, which is correct when the store really said that. A query that failed says nothing
     * at all, and Play fails these for ordinary reasons: the service restarting mid-call, a
     * network that dropped between connecting and asking. Returning an empty list there would
     * confiscate features somebody paid for, on a bad connection, silently - the exact failure the
     * cached license exists to prevent, arriving through a different door. So a failed query leaves
     * the last known answer standing.
     */
    private suspend fun refreshPurchases(): Set<PurchaseRecord>? {
        val subscriptions = queryOwned(PremiumProducts.TYPE_SUBSCRIPTION) ?: return null
        val oneTime = queryOwned(PremiumProducts.TYPE_ONE_TIME) ?: return null
        val owned = (subscriptions + oneTime).toSet()
        _purchases.value = owned
        return owned
    }

    /** What this account owns of [type], or null when the store could not be asked. */
    private suspend fun queryOwned(type: String): List<PurchaseRecord>? {
        val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
        val response = client.queryPurchasesAsync(params)
        if (response.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            AppLog.w(TAG) { "cannot read owned $type: ${response.billingResult.responseCode}" }
            return null
        }
        return response.purchasesList.mapNotNull(::toRecord)
    }

    /**
     * Play's [Purchase] as this app's [PurchaseRecord], or null when it is not something to act on.
     *
     * Two cases are dropped deliberately. A `PENDING` purchase is one Play has not taken money for
     * yet - cash at a kiosk, a parent's approval - and treating it as owned would hand out the app
     * for a payment that may never arrive. An unrecognised product id means [PremiumProducts.tierFor]
     * said no, and guessing a tier there is how a renamed product silently unlocks everything.
     */
    private fun toRecord(purchase: Purchase): PurchaseRecord? {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return null
        val productId = purchase.products.firstOrNull() ?: return null
        val tier = PremiumProducts.tierFor(productId) ?: run {
            AppLog.w(TAG) { "owned product is not in this build's catalogue: $productId" }
            return null
        }
        purchaseTokens[productId] = purchase.purchaseToken
        return PurchaseRecord(
            productId = productId,
            tier = tier,
            purchasedAtMillis = purchase.purchaseTime,
            expiresAtMillis = null,
            needsAcknowledgement = !purchase.isAcknowledged,
        )
    }

    private fun outcomeOf(result: BillingResult, record: PurchaseRecord?): PurchaseResult = when (result.responseCode) {
        BillingClient.BillingResponseCode.OK ->
            record?.let(PurchaseResult::Success) ?: PurchaseResult.Failed("no purchase in an OK result")
        // Closing the sheet is a decision, not a failure, and must not produce an error message.
        BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseResult.Cancelled
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseResult.AlreadyOwned
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
        -> PurchaseResult.Unavailable
        // The message is Play's own and can name the account or the product; it goes no further
        // than the caller, which shows a generic failure.
        else -> PurchaseResult.Failed(result.debugMessage.ifBlank { null })
    }

    /**
     * Three days, not a nicety. Play automatically refunds any purchase left unacknowledged for
     * that long, so a provider that skips this reverses its own sales - quietly, and only for the
     * users who bought early enough to hit the window.
     */
    override suspend fun acknowledge(purchase: PurchaseRecord) {
        if (!purchase.needsAcknowledgement) return
        val token = purchaseTokens[purchase.productId] ?: return
        val result = client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            AppLog.w(TAG) { "acknowledge failed for ${purchase.productId}: ${result.responseCode}" }
        }
    }
}
