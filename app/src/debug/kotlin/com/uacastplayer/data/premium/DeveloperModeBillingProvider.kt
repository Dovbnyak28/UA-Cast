package com.uacastplayer.data.premium

import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseStorage
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.premium.billing.BillingConnectionState
import com.uacastplayer.premium.billing.BillingProduct
import com.uacastplayer.premium.billing.BillingProvider
import com.uacastplayer.premium.billing.PurchaseRecord
import com.uacastplayer.premium.billing.PurchaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the premium layer into any state a user could be in, without Google Play, a network, or a
 * real purchase.
 *
 * **This file is in `src/debug`.** It is not compiled into a release build, so the shipped APK
 * contains no code capable of granting a license - which is a stronger guarantee than a
 * `BuildConfig.DEBUG` check, since that leaves the granting code in the APK behind one condition.
 *
 * Two of the seven states cannot be expressed as purchases at all, which is why this writes to
 * [LicenseStorage] as well as reporting purchases: a trial is granted by the app rather than sold,
 * and an expired subscription is a stored license the store no longer lists.
 */
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
        BillingProduct("dev_monthly", LicenseTier.MONTHLY, "Monthly (developer)", "—"),
        BillingProduct("dev_yearly", LicenseTier.YEARLY, "Yearly (developer)", "—"),
        BillingProduct("dev_lifetime", LicenseTier.LIFETIME, "Lifetime (developer)", "—"),
    )

    override suspend fun purchase(product: BillingProduct, launchContext: Any?): PurchaseResult {
        val record = PurchaseRecord(
            productId = product.id,
            tier = product.tier,
            purchasedAtMillis = System.currentTimeMillis(),
            expiresAtMillis = expiryFor(product.tier),
        )
        _purchases.value = _purchases.value + record
        return PurchaseResult.Success(record)
    }

    override suspend fun restore(): PurchaseResult =
        _purchases.value.firstOrNull()?.let { PurchaseResult.Success(it) } ?: PurchaseResult.Unavailable

    override suspend fun acknowledge(purchase: PurchaseRecord) = Unit

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000

        /** The states the developer menu offers, in the order it lists them. */
        val STATES = listOf("FREE", "TRIAL", "PREMIUM", "LIFETIME", "EXPIRED", "REFUND", "OFFLINE")

        private fun expiryFor(tier: LicenseTier): Long? = when (tier) {
            LicenseTier.MONTHLY -> System.currentTimeMillis() + 30 * DAY
            LicenseTier.YEARLY -> System.currentTimeMillis() + 365 * DAY
            else -> null
        }

        /**
         * Puts the app into [state] and returns the provider that keeps it there.
         *
         * Each state is described by what a real user in it would be holding, not by poking the UI:
         *
         * - **FREE** - never paid. Store connected and listing nothing.
         * - **TRIAL** - the 14 days a fresh install is granted. No store issues this, so it is
         *   written to storage directly.
         * - **PREMIUM** - a running monthly subscription.
         * - **LIFETIME** - bought once, no expiry.
         * - **EXPIRED** - a subscription that ran out. Stored as a *past* expiry so the app has to
         *   distinguish "lapsed" from "never paid", which is exactly the case a boolean would lose.
         * - **REFUND** - the device still holds a paid license and the connected store now lists
         *   nothing. This is the one that must actually revoke access, and the only difference
         *   between it and OFFLINE is whether the store is reachable.
         * - **OFFLINE** - a paid license and an unreachable store. Access must survive. This is the
         *   aeroplane case, and the reason the repository caches at all.
         */
        fun apply(state: String, storage: LicenseStorage): BillingProvider {
            val now = System.currentTimeMillis()
            return when (state) {
                "TRIAL" -> {
                    storage.storedLicense = License.trialStartingAt(now)
                    DeveloperModeBillingProvider()
                }

                "PREMIUM" -> {
                    storage.storedLicense = null
                    DeveloperModeBillingProvider(
                        purchases0 = setOf(record("dev_monthly", LicenseTier.MONTHLY, now + 30 * DAY, now)),
                    )
                }

                "LIFETIME" -> {
                    storage.storedLicense = null
                    DeveloperModeBillingProvider(
                        purchases0 = setOf(record("dev_lifetime", LicenseTier.LIFETIME, null, now)),
                    )
                }

                "EXPIRED" -> {
                    storage.storedLicense = License(LicenseTier.MONTHLY, now - DAY, "dev_monthly")
                    DeveloperModeBillingProvider(connection0 = BillingConnectionState.DISCONNECTED)
                }

                "REFUND" -> {
                    storage.storedLicense = License(LicenseTier.LIFETIME, null, "dev_lifetime")
                    DeveloperModeBillingProvider()
                }

                "OFFLINE" -> {
                    storage.storedLicense = License(LicenseTier.LIFETIME, null, "dev_lifetime")
                    DeveloperModeBillingProvider(connection0 = BillingConnectionState.UNAVAILABLE)
                }

                // FREE, and anything unrecognised: nothing stored, connected store listing nothing.
                else -> {
                    storage.storedLicense = License.FREE
                    DeveloperModeBillingProvider()
                }
            }
        }

        private fun record(id: String, tier: LicenseTier, expiry: Long?, now: Long) =
            PurchaseRecord(productId = id, tier = tier, purchasedAtMillis = now, expiresAtMillis = expiry)
    }
}
