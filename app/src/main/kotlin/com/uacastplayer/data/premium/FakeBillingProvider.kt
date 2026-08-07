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
 * The provider a released build uses until this app is actually published: it reports that there is
 * no store, offers nothing, and owns nothing.
 *
 * This is the honest state of the world right now, not a placeholder that pretends. Nothing is for
 * sale, so the Premium screen shows what premium *is* and no prices, and every paid feature stays
 * locked unless a trial is running. On the day Google Play Billing is added, this class is replaced
 * at one construction site and deleted; nothing else changes.
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
