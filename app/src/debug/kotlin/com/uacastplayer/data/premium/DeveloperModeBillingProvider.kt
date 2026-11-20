package com.uacastplayer.data.premium

import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseStorage
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.premium.billing.BillingConnectionState
import com.uacastplayer.premium.billing.BillingProduct
import com.uacastplayer.premium.billing.BillingProvider
import com.uacastplayer.premium.billing.PremiumProducts
import com.uacastplayer.premium.billing.PurchaseRecord
import com.uacastplayer.premium.billing.PurchaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Isolated debug provider for Lite, Premium, refund, offline and legacy expiry regressions.
 * This granting implementation is compiled only into debug APKs. */
class DeveloperModeBillingProvider(
    private val purchases0: Set<PurchaseRecord> = emptySet(),
    connection0: BillingConnectionState = BillingConnectionState.CONNECTED,
) : BillingProvider {
    private val _connection = MutableStateFlow(connection0)
    override val connection: StateFlow<BillingConnectionState> = _connection.asStateFlow()
    private val _purchases = MutableStateFlow(purchases0)
    override val purchases: StateFlow<Set<PurchaseRecord>> = _purchases.asStateFlow()

    override suspend fun connect() = Unit

    override suspend fun products(): List<BillingProduct> = listOf(
        BillingProduct(PremiumProducts.LIFETIME, LicenseTier.LIFETIME, "Premium (developer)", "—"),
    )

    override suspend fun purchase(product: BillingProduct, launchContext: Any?): PurchaseResult {
        if (!PremiumProducts.isForSale(product.id)) return PurchaseResult.Unavailable
        val record = record(System.currentTimeMillis())
        _purchases.value = _purchases.value + record
        return PurchaseResult.Success(record)
    }

    override suspend fun restore(): PurchaseResult =
        _purchases.value.firstOrNull()?.let(PurchaseResult::Success) ?: PurchaseResult.NothingToRestore

    override suspend fun acknowledge(purchase: PurchaseRecord) = Unit

    companion object {
        val STATES = listOf("LITE", "PREMIUM", "REFUND", "OFFLINE", "EXPIRED")

        fun apply(state: String, storage: LicenseStorage): BillingProvider {
            val now = System.currentTimeMillis()
            val paid = License(LicenseTier.LIFETIME, source = PremiumProducts.LIFETIME)
            return when (state) {
                "PREMIUM" -> {
                    storage.storedLicense = paid
                    DeveloperModeBillingProvider(purchases0 = setOf(record(now)))
                }
                "REFUND" -> {
                    storage.storedLicense = paid
                    DeveloperModeBillingProvider()
                }
                "OFFLINE" -> {
                    storage.storedLicense = paid
                    DeveloperModeBillingProvider(connection0 = BillingConnectionState.UNAVAILABLE)
                }
                "EXPIRED" -> {
                    storage.storedLicense = paid.copy(expiresAtMillis = now - 1)
                    DeveloperModeBillingProvider(connection0 = BillingConnectionState.DISCONNECTED)
                }
                else -> {
                    storage.storedLicense = License.FREE
                    DeveloperModeBillingProvider()
                }
            }
        }

        private fun record(now: Long) = PurchaseRecord(
            productId = PremiumProducts.LIFETIME,
            tier = LicenseTier.LIFETIME,
            purchasedAtMillis = now,
            expiresAtMillis = null,
        )
    }
}
