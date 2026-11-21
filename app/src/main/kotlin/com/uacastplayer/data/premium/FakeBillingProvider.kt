package com.uacastplayer.data.premium

import com.uacastplayer.premium.billing.BillingConnectionState
import com.uacastplayer.premium.billing.BillingProduct
import com.uacastplayer.premium.billing.BillingProvider
import com.uacastplayer.premium.billing.PurchaseRecord
import com.uacastplayer.premium.billing.PurchaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The provider the sideloaded GitHub release and debug builds use: it reports that there is no
 * store, offers nothing, and owns nothing.
 *
 * Google Play can only charge for a build it installed, so outside the `play` build type this is
 * the honest state of the world, not a placeholder that pretends. Nothing is for sale, so the
 * Premium screen shows what premium *is* and no prices, and every paid feature stays locked unless
 * a paid licence is already cached. The `play` build uses [PlayBillingProvider] instead - see
 * [com.uacastplayer.premium.PremiumAvailability].
 *
 * It is deliberately not the developer menu. That one grants licenses, lives in `src/debug`, and is
 * not compiled into a release build at all.
 */
class FakeBillingProvider : BillingProvider {

    private val _connection = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    override val connection: StateFlow<BillingConnectionState> = _connection.asStateFlow()

    private val _purchases = MutableStateFlow<Set<PurchaseRecord>>(emptySet())
    override val purchases: StateFlow<Set<PurchaseRecord>> = _purchases.asStateFlow()

    override suspend fun connect() {
        // UNAVAILABLE rather than DISCONNECTED: there is no store to reconnect to, and the
        // difference is what stops the UI offering a pointless "try again".
        _connection.value = BillingConnectionState.UNAVAILABLE
    }

    override suspend fun products(): List<BillingProduct> = emptyList()

    override suspend fun purchase(product: BillingProduct, launchContext: Any?): PurchaseResult =
        PurchaseResult.Unavailable

    override suspend fun restore(): PurchaseResult = PurchaseResult.Unavailable

    override suspend fun acknowledge(purchase: PurchaseRecord) = Unit
}
