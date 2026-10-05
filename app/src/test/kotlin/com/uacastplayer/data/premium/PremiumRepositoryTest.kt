package com.uacastplayer.data.premium

import com.uacastplayer.premium.Feature
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseStorage
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.premium.billing.BillingConnectionState
import com.uacastplayer.premium.billing.BillingProduct
import com.uacastplayer.premium.billing.BillingProvider
import com.uacastplayer.premium.billing.PurchaseRecord
import com.uacastplayer.premium.billing.PurchaseResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PremiumRepositoryTest {

    private val now = 1_800_000_000_000L

    /**
     * Unconfined on purpose. The repository's store listener is an endless `collect`, and under the
     * default `StandardTestDispatcher` the coroutine holding it is queued and never actually starts,
     * so every purchase in these tests would be delivered to nobody. Unconfined runs the launch
     * eagerly to its first suspension, which is exactly the production situation being modelled: by
     * the time a purchase arrives, something is already listening.
     */
    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        scope.cancel()
    }

    private class FakeStorage(
        override var storedLicense: License? = null,
        override var clockHighWaterMark: Long = 0L,
    ) : LicenseStorage

    private class StubProvider(private val catalogue: List<BillingProduct> = emptyList()) : BillingProvider {
        val connectionFlow = MutableStateFlow(BillingConnectionState.DISCONNECTED)
        val purchasesFlow = MutableStateFlow<Set<PurchaseRecord>?>(emptySet())
        var acknowledged = mutableListOf<PurchaseRecord>()
        var acknowledgementAttempts = mutableListOf<PurchaseRecord>()
        var acknowledgementFailures = emptySet<String>()
        var catalogueQueries = 0
        var catalogueGate: CompletableDeferred<Unit>? = null
        val launchedPurchases = mutableListOf<String>()
        var purchaseResult: PurchaseResult = PurchaseResult.Unavailable
        var purchaseGate: CompletableDeferred<PurchaseResult>? = null
        var closeCalls = 0

        override val connection: StateFlow<BillingConnectionState> = connectionFlow.asStateFlow()
        override val purchases: StateFlow<Set<PurchaseRecord>?> = purchasesFlow.asStateFlow()
        override suspend fun connect() = Unit
        override suspend fun products(): List<BillingProduct> {
            catalogueQueries++
            catalogueGate?.await()
            return catalogue
        }
        override suspend fun purchase(product: BillingProduct, launchContext: Any?): PurchaseResult {
            launchedPurchases += product.id
            return purchaseGate?.await() ?: purchaseResult
        }
        override suspend fun restore() = PurchaseResult.Unavailable
        override fun close() { closeCalls++ }
        override suspend fun acknowledge(purchase: PurchaseRecord) {
            acknowledgementAttempts += purchase
            if (purchase.productId in acknowledgementFailures) {
                throw IllegalStateException("billing acknowledgement failed")
            }
            acknowledged += purchase
        }
    }

    private class FailingProvider : BillingProvider {
        private val connectionFlow = MutableStateFlow(BillingConnectionState.DISCONNECTED)
        private val purchasesFlow = MutableStateFlow<Set<PurchaseRecord>>(emptySet())

        override val connection: StateFlow<BillingConnectionState> = connectionFlow.asStateFlow()
        override val purchases: StateFlow<Set<PurchaseRecord>> = purchasesFlow.asStateFlow()
        override suspend fun connect(): Unit = throw IllegalStateException("connect failed")
        override suspend fun products(): List<BillingProduct> = throw IllegalStateException("catalogue failed")
        override suspend fun purchase(product: BillingProduct, launchContext: Any?): PurchaseResult =
            throw IllegalStateException("purchase failed")
        override suspend fun restore(): PurchaseResult = throw IllegalStateException("restore failed")
        override suspend fun acknowledge(purchase: PurchaseRecord): Unit =
            throw IllegalStateException("acknowledge failed")
    }

    private fun purchase(
        tier: LicenseTier,
        expires: Long? = null,
        id: String = "product",
        needsAck: Boolean = false,
    ) = PurchaseRecord(id, tier, now, expires, needsAck)

    @Test fun connectedButUnknownOwnershipPreservesCachedPaidLicenseUntilARealAnswer() = runTest {
        val storage = FakeStorage(storedLicense = License(LicenseTier.LIFETIME))
        val provider = StubProvider()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        provider.purchasesFlow.value = null
        val repository = PremiumRepository(provider, storage, scope) { now }
        repository.loadInitial()
        assertEquals(LicenseTier.LIFETIME, storage.storedLicense?.tier)
        provider.purchasesFlow.value = emptySet()
        assertEquals(LicenseTier.FREE, storage.storedLicense?.tier)
    }

    @Test fun ownershipGrantDoesNotWaitForOrQueryTheSaleCatalogue() = runTest {
        val storage = FakeStorage(License.FREE)
        val provider = StubProvider().apply {
            catalogueGate = CompletableDeferred()
            purchasesFlow.value = setOf(purchase(LicenseTier.LIFETIME))
            connectionFlow.value = BillingConnectionState.CONNECTED
        }
        val repository = PremiumRepository(provider, storage, scope) { now }

        repository.loadInitial()

        assertEquals(LicenseTier.LIFETIME, repository.entitlements.value.license.tier)
        assertEquals(0, provider.catalogueQueries)
    }

    @Test fun authoritativeRefundDoesNotWaitForOrQueryTheSaleCatalogue() = runTest {
        val storage = FakeStorage(License(LicenseTier.LIFETIME))
        val provider = StubProvider().apply {
            catalogueGate = CompletableDeferred()
            connectionFlow.value = BillingConnectionState.CONNECTED
        }
        val repository = PremiumRepository(provider, storage, scope) { now }

        repository.loadInitial()

        assertEquals(LicenseTier.FREE, repository.entitlements.value.license.tier)
        assertEquals(0, provider.catalogueQueries)
    }

    @Test fun replacingAStoreReleasesItsSdkOwner() = runTest {
        val first = StubProvider()
        val second = StubProvider()
        val repository = PremiumRepository(first, FakeStorage(License.FREE), scope) { now }
        repository.loadInitial()

        repository.useProvider(second)

        assertEquals(1, first.closeCalls)
        assertEquals(0, second.closeCalls)
    }

    @Test fun retiredProviderPurchaseCannotOverwriteReplacementOwnership() = runTest {
        val first = StubProvider(catalogue()).apply { purchaseGate = CompletableDeferred() }
        val storage = FakeStorage(License.FREE)
        val repository = PremiumRepository(first, storage, scope) { now }
        repository.loadInitial()
        val checkout = async { repository.purchase(catalogue().single().id, null) }
        runCurrent()
        assertEquals(1, first.launchedPurchases.size)

        repository.useProvider(StubProvider().apply { connectionFlow.value = BillingConnectionState.CONNECTED })
        first.purchaseGate!!.complete(PurchaseResult.Success(purchase(LicenseTier.LIFETIME)))

        assertEquals(PurchaseResult.Unavailable, checkout.await())
        assertEquals(LicenseTier.FREE, repository.entitlements.value.license.tier)
        assertEquals(LicenseTier.FREE, storage.storedLicense?.tier)
    }

    @Test fun closeIsIdempotentAndCannotRevokeCachedPaidAccess() = runTest {
        val paid = License(LicenseTier.LIFETIME, source = "premium_lifetime")
        val provider = StubProvider()
        val storage = FakeStorage(paid)
        val repository = PremiumRepository(provider, storage, scope) { now }
        repository.loadInitial()

        repository.close()
        repository.close()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        repository.loadInitial()
        repository.refresh()

        assertEquals(1, provider.closeCalls)
        assertEquals(paid, storage.storedLicense)
        assertEquals(LicenseTier.LIFETIME, repository.entitlements.value.effectiveTier)
        assertEquals(emptyList<BillingProduct>(), repository.products())
        assertEquals(PurchaseResult.Unavailable, repository.purchase("premium_lifetime", null))
        assertEquals(PurchaseResult.Unavailable, repository.restore())
        assertEquals(0, provider.catalogueQueries)
        assertTrue(provider.launchedPurchases.isEmpty())
    }

    @Test fun reusingTheSameProviderDoesNotCloseTheActiveStore() = runTest {
        val provider = StubProvider()
        val repository = PremiumRepository(provider, FakeStorage(License.FREE), scope) { now }
        repository.loadInitial()

        repository.useProvider(provider)
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        provider.purchasesFlow.value = setOf(purchase(LicenseTier.LIFETIME))

        assertEquals(0, provider.closeCalls)
        assertEquals(LicenseTier.LIFETIME, repository.entitlements.value.effectiveTier)
    }

    @Test fun providerPassedToADestroyedOwnerIsReleasedWithoutBeingObserved() = runTest {
        val first = StubProvider()
        val second = StubProvider()
        val repository = PremiumRepository(first, FakeStorage(License.FREE), scope) { now }
        repository.loadInitial()
        repository.close()

        repository.useProvider(second)
        second.connectionFlow.value = BillingConnectionState.CONNECTED
        second.purchasesFlow.value = setOf(purchase(LicenseTier.LIFETIME))

        assertEquals(1, first.closeCalls)
        assertEquals(1, second.closeCalls)
        assertEquals(LicenseTier.FREE, repository.entitlements.value.effectiveTier)
    }

    @Test fun aLateCatalogueResultFromARetiredStoreIsNotReturnedToTheUi() = runTest {
        val provider = StubProvider(catalogue()).apply { catalogueGate = CompletableDeferred() }
        val repository = PremiumRepository(provider, FakeStorage(License.FREE), scope) { now }
        repository.loadInitial()
        val prices = async { repository.products() }
        runCurrent()
        assertEquals(1, provider.catalogueQueries)

        repository.useProvider(StubProvider())
        provider.catalogueGate!!.complete(Unit)

        assertTrue(prices.await().isEmpty())
    }

    @Test
    fun aFreshInstallStartsInLiteAndItIsRemembered() = runTest {
        val storage = FakeStorage(storedLicense = null)
        val repository = PremiumRepository(StubProvider(), storage, scope) { now }

        repository.loadInitial()

        assertEquals(LicenseTier.FREE, repository.entitlements.value.license.tier)
        assertFalse(repository.entitlements.value.unlocked.contains(Feature.DLNA))
        assertEquals(License.FREE, storage.storedLicense)
    }

    /** Clearing an expired trial would hand the same device a fresh 14 days on every launch. */
    @Test
    fun aLegacyTrialIsRetiredToLite() = runTest {
        val expired = License(LicenseTier.TRIAL, expiresAtMillis = now - 1, source = "trial")
        val storage = FakeStorage(storedLicense = expired)
        val repository = PremiumRepository(StubProvider(), storage, scope) { now }

        repository.loadInitial()

        assertEquals(LicenseTier.FREE, repository.entitlements.value.license.tier)
        assertFalse(repository.entitlements.value.hasLapsed)
        assertFalse(repository.entitlements.value.unlocked.contains(Feature.DLNA))
        assertTrue(repository.entitlements.value.unlocked.contains(Feature.CHROMECAST))
    }

    /**
     * The aeroplane test, and the reason the repository exists at all: a store that cannot be
     * reached says nothing, and nothing must not be read as "you own nothing".
     */
    @Test
    fun aDisconnectedStoreDoesNotRevokeAPaidLicense() = runTest {
        val paid = License(LicenseTier.LIFETIME, expiresAtMillis = null, source = "lifetime")
        val storage = FakeStorage(storedLicense = paid)
        val provider = StubProvider()
        val repository = PremiumRepository(provider, storage, scope) { now }

        repository.loadInitial()

        // The store comes up empty while disconnected - the ordinary offline case.
        provider.purchasesFlow.value = emptySet()

        assertEquals(LicenseTier.LIFETIME, repository.entitlements.value.license.tier)
        assertTrue(repository.entitlements.value.unlocked.contains(Feature.DLNA))
        assertEquals(paid, storage.storedLicense)
    }

    /** Once the store is actually connected and still reports nothing, a paid license really has
     * ended - cancelled or refunded - and it goes. */
    @Test
    fun aConnectedStoreReportingNothingClearsAPaidLicense() = runTest {
        val storage = FakeStorage(storedLicense = License(LicenseTier.MONTHLY, now + 1_000_000, "monthly"))
        val provider = StubProvider()
        val repository = PremiumRepository(provider, storage, scope) { now }

        repository.loadInitial()

        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        provider.purchasesFlow.value = emptySet()

        assertEquals(LicenseTier.FREE, repository.entitlements.value.license.tier)
        assertFalse(repository.entitlements.value.unlocked.contains(Feature.DLNA))
    }

    /** A running trial was never issued by a store, so a store reporting no purchases says nothing
     * about it. */
    @Test
    fun aRunningLegacyTrialCannotGrantPremium() = runTest {
        val storage = FakeStorage(storedLicense = License(LicenseTier.TRIAL, expiresAtMillis = now + 1000))
        val provider = StubProvider()
        val repository = PremiumRepository(provider, storage, scope) { now }

        repository.loadInitial()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        provider.purchasesFlow.value = emptySet()

        assertEquals(LicenseTier.FREE, repository.entitlements.value.license.tier)
        assertFalse(repository.entitlements.value.unlocked.contains(Feature.DLNA))
    }

    @Test
    fun aPurchaseFromAConnectedStoreBecomesTheLicense() = runTest {
        val storage = FakeStorage(storedLicense = License.FREE)
        val provider = StubProvider()
        val repository = PremiumRepository(provider, storage, scope) { now }

        repository.loadInitial()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        provider.purchasesFlow.value = setOf(purchase(LicenseTier.YEARLY, expires = now + 1_000_000, id = "yearly"))

        assertEquals(LicenseTier.LIFETIME, repository.entitlements.value.license.tier)
        assertEquals(now + 1_000_000, repository.entitlements.value.license.expiresAtMillis)
        assertEquals("yearly", repository.entitlements.value.license.source)
        assertTrue(repository.entitlements.value.unlocked.contains(Feature.CLOUD_SYNC))
    }

    /** Someone can hold more than one at a time; the app should honour the best of them. */
    @Test
    fun lifetimeWinsOverAStillRunningSubscription() = runTest {
        val provider = StubProvider()
        val repository = PremiumRepository(provider, FakeStorage(License.FREE), scope) { now }

        repository.loadInitial()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        provider.purchasesFlow.value = setOf(
            purchase(LicenseTier.MONTHLY, expires = now + 1000, id = "monthly"),
            purchase(LicenseTier.LIFETIME, expires = null, id = "lifetime"),
        )

        assertEquals(LicenseTier.LIFETIME, repository.entitlements.value.license.tier)
    }

    @Test
    fun anExpiredPurchaseIsIgnoredInFavourOfOneThatStillRuns() = runTest {
        val provider = StubProvider()
        val repository = PremiumRepository(provider, FakeStorage(License.FREE), scope) { now }

        repository.loadInitial()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        provider.purchasesFlow.value = setOf(
            purchase(LicenseTier.YEARLY, expires = now - 1, id = "old-yearly"),
            purchase(LicenseTier.MONTHLY, expires = now + 5000, id = "monthly"),
        )

        assertEquals(LicenseTier.LIFETIME, repository.entitlements.value.license.tier)
    }

    /** Google Play refunds anything unacknowledged within three days: skipping this silently
     * reverses a sale that already went through. */
    @Test
    fun aPurchaseNeedingAcknowledgementGetsOne() = runTest {
        val provider = StubProvider()
        val repository = PremiumRepository(provider, FakeStorage(License.FREE), scope) { now }

        repository.loadInitial()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        provider.purchasesFlow.value = setOf(purchase(LicenseTier.LIFETIME, id = "lifetime", needsAck = true))

        assertEquals(1, provider.acknowledged.size)
        assertEquals("lifetime", provider.acknowledged.single().productId)
    }

    /**
     * **Every** purchase needing acknowledgement gets one, not only the best of them.
     *
     * Acknowledgement used to be sent for whichever purchase `bestOf` selected - the one that
     * decides the licence. That is the wrong set: Play refunds each unacknowledged purchase on its
     * own three-day clock, and it does not care which of them this app considered best. So a user
     * who owns two - an active subscription beside a lifetime bought on top of it, or two whose
     * first acknowledgement failed on a bad connection - had one of them quietly reversed while
     * keeping the features, and the developer lost the money without anything failing anywhere.
     *
     * Both are unacknowledged here because that is the shape that loses money; the usual case, where
     * the older one was acknowledged long ago, loses nothing either way.
     */
    @Test
    fun everyUnacknowledgedPurchaseGetsAcknowledged() = runTest {
        val provider = StubProvider()
        val repository = PremiumRepository(provider, FakeStorage(License.FREE), scope) { now }

        repository.loadInitial()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        provider.purchasesFlow.value = setOf(
            purchase(LicenseTier.LIFETIME, id = "lifetime", needsAck = true),
            purchase(LicenseTier.YEARLY, expires = now + 1_000_000, id = "yearly", needsAck = true),
        )

        assertEquals(
            "both purchases must be acknowledged, or Play refunds the one that was not",
            setOf("lifetime", "yearly"),
            provider.acknowledged.map { it.productId }.toSet(),
        )
    }

    @Test
    fun oneFailedAcknowledgementDoesNotPreventTheRemainingPurchaseFromBeingAttempted() = runTest {
        val provider = StubProvider().apply { acknowledgementFailures = setOf("first") }
        val repository = PremiumRepository(provider, FakeStorage(License.FREE), scope) { now }

        repository.loadInitial()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        provider.purchasesFlow.value = linkedSetOf(
            purchase(LicenseTier.YEARLY, expires = now + 1_000_000, id = "first", needsAck = true),
            purchase(LicenseTier.LIFETIME, id = "second", needsAck = true),
        )

        assertEquals(listOf("first", "second"), provider.acknowledgementAttempts.map { it.productId })
        assertEquals(listOf("second"), provider.acknowledged.map { it.productId })
    }

    /** The control: an acknowledgement already given must not be sent again on every store update -
     * `applyPurchases` runs on each connection and each refresh. */
    @Test
    fun aPurchaseAlreadyAcknowledgedIsLeftAlone() = runTest {
        val provider = StubProvider()
        val repository = PremiumRepository(provider, FakeStorage(License.FREE), scope) { now }

        repository.loadInitial()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        provider.purchasesFlow.value = setOf(
            purchase(LicenseTier.LIFETIME, id = "lifetime", needsAck = false),
            purchase(LicenseTier.YEARLY, expires = now + 1_000_000, id = "yearly", needsAck = true),
        )

        assertEquals(listOf("yearly"), provider.acknowledged.map { it.productId })
    }

    /** A restored legacy subscription retains its original expiry while the app is open. */
    @Test
    fun refreshEndsLegacyPaidAccessThatRanOutWhileTheAppWasOpen() = runTest {
        val storage = FakeStorage(License(LicenseTier.MONTHLY, now + 1_000, "premium_monthly"))
        var clock = now
        val repository = PremiumRepository(StubProvider(), storage, scope) { clock }

        repository.loadInitial()
        assertTrue(repository.entitlements.value.unlocked.contains(Feature.DLNA))

        clock = now + 1_000
        repository.refresh()

        assertFalse(repository.entitlements.value.unlocked.contains(Feature.DLNA))
        assertTrue(repository.entitlements.value.hasLapsed)
    }

    private fun catalogue() = listOf(
        BillingProduct("premium_lifetime", LicenseTier.LIFETIME, "Premium", "$1.99"),
    )

    @Test fun oldPlansAreFilteredAndCannotLaunchCheckout() = runTest {
        val legacy = listOf(
            BillingProduct("premium_monthly", LicenseTier.MONTHLY, "Monthly", "$1.99"),
            BillingProduct("premium_yearly", LicenseTier.YEARLY, "Yearly", "$19.99"),
        )
        val provider = StubProvider(legacy + catalogue() + catalogue())
        val repository = PremiumRepository(provider, FakeStorage(License.FREE), scope) { now }
        repository.loadInitial()
        assertEquals(listOf("premium_lifetime"), repository.products().map { it.id })
        for (id in listOf("premium_monthly", "premium_yearly", "unknown")) {
            assertEquals(PurchaseResult.Unavailable, repository.purchase(id, null))
        }
        assertTrue(provider.launchedPurchases.isEmpty())
    }

    @Test fun aSinglePurchaseUnlocksEverythingAndIsStoredWithoutExpiry() = runTest {
        val provider = StubProvider(catalogue()).apply {
            purchaseResult = PurchaseResult.Success(purchase(LicenseTier.LIFETIME, id = "premium_lifetime"))
        }
        val storage = FakeStorage()
        val repository = PremiumRepository(provider, storage, scope) { now }
        repository.loadInitial()
        assertFalse(Feature.DLNA in repository.entitlements.value.unlocked)
        assertTrue(repository.purchase("premium_lifetime", null) is PurchaseResult.Success)
        assertEquals(Feature.entries.toSet(), repository.entitlements.value.unlocked)
        assertEquals(License(LicenseTier.LIFETIME, source = "premium_lifetime"), storage.storedLicense)
        assertEquals(listOf("premium_lifetime"), provider.launchedPurchases)
    }

    @Test fun legacyPaidStorageMigratesWithoutExtendingOrLosingAccess() = runTest {
        val legacy = License(LicenseTier.YEARLY, now + 1_000, "premium_yearly")
        val storage = FakeStorage(legacy)
        val repository = PremiumRepository(StubProvider(), storage, scope) { now }
        repository.loadInitial()
        assertEquals(legacy.copy(tier = LicenseTier.LIFETIME), storage.storedLicense)
        assertEquals(Feature.entries.toSet(), repository.entitlements.value.unlocked)
    }

    @Test fun repeatedOwnershipUpdatesDoNotQueryPrices() = runTest {
        val provider = StubProvider(catalogue = catalogue())
        val repository = PremiumRepository(provider, FakeStorage(License.FREE), scope) { now }
        repository.loadInitial()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED

        repeat(10) {
            provider.purchasesFlow.value = setOf(purchase(LicenseTier.LIFETIME))
            assertEquals(LicenseTier.LIFETIME, repository.entitlements.value.license.tier)
            provider.purchasesFlow.value = emptySet()
            assertEquals(LicenseTier.FREE, repository.entitlements.value.license.tier)
        }

        assertEquals(0, provider.catalogueQueries)
    }

    /** Clearing stored data still starts in Lite; no retired trial can be recreated. */
    @Test
    fun clearingDataOnAnOldInstallDoesNotHandOutAnotherTrial() = runTest {
        val storage = FakeStorage(storedLicense = null)
        val repository = PremiumRepository(StubProvider(), storage, scope) { now }

        repository.loadInitial()

        assertEquals(LicenseTier.FREE, repository.entitlements.value.license.tier)
        assertFalse(repository.entitlements.value.unlocked.contains(Feature.DLNA))
        assertNotNull("the decision has to be recorded, not retaken every launch", storage.storedLicense)
    }

    /** And a genuine first launch is unaffected by the same check. */
    @Test
    fun repeatedLaunchesWithoutAPurchaseStayInLite() = runTest {
        val storage = FakeStorage(storedLicense = null)
        val repository = PremiumRepository(StubProvider(), storage, scope) { now }

        repository.loadInitial()
        repository.loadInitial()
        assertEquals(LicenseTier.FREE, repository.entitlements.value.license.tier)
    }

    /**
     * Winding the device clock back must not revive legacy paid access that has ended. The repository records
     * the newest time it has seen and judges expiry against that, so the second launch is resolved
     * at the time of the first rather than at whatever the settings screen now claims.
     */
    @Test
    fun windingTheClockBackDoesNotReviveLapsedLegacyPaidAccess() = runTest {
        val storage = FakeStorage(storedLicense = License(LicenseTier.MONTHLY, now + 1_000, "premium_monthly"))
        var clock = now + 1_001

        val afterItLapsed = PremiumRepository(StubProvider(), storage, scope) { clock }
        afterItLapsed.loadInitial()
        assertTrue("legacy paid access has to have lapsed first", afterItLapsed.entitlements.value.hasLapsed)

        clock = now - 30 * 24 * 60 * 60 * 1000L
        val afterWindingBack = PremiumRepository(StubProvider(), storage, scope) { clock }
        afterWindingBack.loadInitial()

        assertTrue("a clock that went backwards is not evidence", afterWindingBack.entitlements.value.hasLapsed)
        assertFalse(afterWindingBack.entitlements.value.unlocked.contains(Feature.DLNA))
    }

    @Test
    fun theFakeProviderSellsNothingAndOwnsNothing() = runTest {
        val provider = FakeBillingProvider()
        provider.connect()

        assertEquals(BillingConnectionState.UNAVAILABLE, provider.connection.value)
        assertTrue(provider.purchases.value.isEmpty())
        assertTrue(provider.products().isEmpty())
        assertEquals(PurchaseResult.Unavailable, provider.restore())
    }

    @Test
    fun unexpectedProviderFailuresStayInsideTheRepositoryBoundary() = runTest {
        val stored = License(LicenseTier.LIFETIME, expiresAtMillis = null, source = "lifetime")
        val repository = PremiumRepository(FailingProvider(), FakeStorage(stored), scope) { now }

        repository.loadInitial()

        assertEquals(BillingConnectionState.DISCONNECTED, repository.connection.value)
        assertEquals(LicenseTier.LIFETIME, repository.entitlements.value.license.tier)
        assertTrue(repository.products().isEmpty())
        assertTrue(repository.purchase("premium_lifetime", null) is PurchaseResult.Failed)
        assertTrue(repository.restore() is PurchaseResult.Failed)
    }
}
