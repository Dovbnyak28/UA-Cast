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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
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
        override var storeHasEverOfferedProducts: Boolean = false,
        override var clockHighWaterMark: Long = 0L,
    ) : LicenseStorage

    private class StubProvider(private val catalogue: List<BillingProduct> = emptyList()) : BillingProvider {
        val connectionFlow = MutableStateFlow(BillingConnectionState.DISCONNECTED)
        val purchasesFlow = MutableStateFlow<Set<PurchaseRecord>>(emptySet())
        var acknowledged = mutableListOf<PurchaseRecord>()
        var catalogueQueries = 0

        override val connection: StateFlow<BillingConnectionState> = connectionFlow.asStateFlow()
        override val purchases: StateFlow<Set<PurchaseRecord>> = purchasesFlow.asStateFlow()
        override suspend fun connect() = Unit
        override suspend fun products(): List<BillingProduct> {
            catalogueQueries++
            return catalogue
        }
        override suspend fun purchase(product: BillingProduct, launchContext: Any?) = PurchaseResult.Unavailable
        override suspend fun restore() = PurchaseResult.Unavailable
        override suspend fun acknowledge(purchase: PurchaseRecord) {
            acknowledged += purchase
        }
    }

    private fun purchase(
        tier: LicenseTier,
        expires: Long? = null,
        id: String = "product",
        needsAck: Boolean = false,
    ) = PurchaseRecord(id, tier, now, expires, needsAck)

    @Test
    fun aFreshInstallIsGrantedATrialAndItIsRemembered() = runTest {
        val storage = FakeStorage(storedLicense = null)
        val repository = PremiumRepository(StubProvider(), storage, scope) { now }

        repository.loadInitial()

        assertEquals(LicenseTier.TRIAL, repository.entitlements.value.license.tier)
        assertTrue(repository.entitlements.value.unlocked.contains(Feature.DLNA))
        assertNotNull("the trial must be stored, or it is granted again next launch", storage.storedLicense)
    }

    /** Clearing an expired trial would hand the same device a fresh 14 days on every launch. */
    @Test
    fun anExpiredTrialIsNotGrantedAgain() = runTest {
        val expired = License.trialStartingAt(now - License.TRIAL_DURATION_MILLIS - 1)
        val storage = FakeStorage(storedLicense = expired)
        val repository = PremiumRepository(StubProvider(), storage, scope) { now }

        repository.loadInitial()

        assertEquals(LicenseTier.TRIAL, repository.entitlements.value.license.tier)
        assertTrue(repository.entitlements.value.hasLapsed)
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
    fun aConnectedStoreReportingNothingLeavesARunningTrialAlone() = runTest {
        val storage = FakeStorage(storedLicense = License.trialStartingAt(now))
        val provider = StubProvider()
        val repository = PremiumRepository(provider, storage, scope) { now }

        repository.loadInitial()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        provider.purchasesFlow.value = emptySet()

        assertEquals(LicenseTier.TRIAL, repository.entitlements.value.license.tier)
        assertTrue(repository.entitlements.value.unlocked.contains(Feature.DLNA))
    }

    @Test
    fun aPurchaseFromAConnectedStoreBecomesTheLicense() = runTest {
        val storage = FakeStorage(storedLicense = License.FREE)
        val provider = StubProvider()
        val repository = PremiumRepository(provider, storage, scope) { now }

        repository.loadInitial()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        provider.purchasesFlow.value = setOf(purchase(LicenseTier.YEARLY, expires = now + 1_000_000, id = "yearly"))

        assertEquals(LicenseTier.YEARLY, repository.entitlements.value.license.tier)
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

        assertEquals(LicenseTier.MONTHLY, repository.entitlements.value.license.tier)
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

    /** Nothing else notices that a trial has ended - a running app has to re-resolve it against
     * the clock. */
    @Test
    fun refreshEndsATrialThatRanOutWhileTheAppWasOpen() = runTest {
        val storage = FakeStorage(License.trialStartingAt(now))
        var clock = now
        val repository = PremiumRepository(StubProvider(), storage, scope) { clock }

        repository.loadInitial()
        assertTrue(repository.entitlements.value.unlocked.contains(Feature.DLNA))

        clock = now + License.TRIAL_DURATION_MILLIS
        repository.refresh()

        assertFalse(repository.entitlements.value.unlocked.contains(Feature.DLNA))
        assertTrue(repository.entitlements.value.hasLapsed)
    }

    private fun catalogue() = listOf(
        BillingProduct("premium_monthly", LicenseTier.MONTHLY, "Monthly", "$1.99"),
    )

    /**
     * The release-day failure this latch exists for.
     *
     * A product id Play Console does not have is not an error: the query succeeds and comes back
     * without it. So a build flipped live one day early, or with one id spelled differently, is a
     * connected store that sells nothing - and every gate hanging off it would be a lock with no
     * key. The repository has to notice, without anything opening the premium screen first.
     */
    @Test
    fun aConnectedStoreWithAnEmptyCatalogueIsNotAllowedToSell() = runTest {
        val storage = FakeStorage(License.FREE)
        val provider = StubProvider(catalogue = emptyList())
        val repository = PremiumRepository(provider, storage, scope) { now }

        repository.loadInitial()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED

        assertFalse(repository.storeCanSell.value)
        assertFalse(storage.storeHasEverOfferedProducts)
        assertTrue("the catalogue must be asked for without a screen doing it", provider.catalogueQueries > 0)
    }

    @Test
    fun aStoreThatOffersAPriceMayThenSell() = runTest {
        val storage = FakeStorage(License.FREE)
        val provider = StubProvider(catalogue = catalogue())
        val repository = PremiumRepository(provider, storage, scope) { now }

        repository.loadInitial()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED

        assertTrue(repository.storeCanSell.value)
        assertTrue("it has to survive the next launch", storage.storeHasEverOfferedProducts)
    }

    /** Offline is not a licence to give the app away. Once the till has been seen open, it stays
     * open - which is the whole reason the flag is stored rather than re-asked. */
    @Test
    fun aDeviceThatHasSeenPricesBeforeStaysSellableWithNoStoreAtAll() = runTest {
        val storage = FakeStorage(License.FREE, storeHasEverOfferedProducts = true)
        val provider = StubProvider(catalogue = emptyList())
        val repository = PremiumRepository(provider, storage, scope) { now }

        repository.loadInitial()

        assertTrue(repository.storeCanSell.value)
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        assertTrue("an empty answer must not close a till that was already open", repository.storeCanSell.value)
        assertEquals("nothing left to find out: do not ask again", 0, provider.catalogueQueries)
    }

    /** The catalogue is queried to answer one question, and the answer does not change. Re-asking
     * it on every purchase update would be a network call per store event, forever. */
    @Test
    fun theCatalogueIsAskedAboutOnlyUntilItAnswers() = runTest {
        val provider = StubProvider(catalogue = catalogue())
        val repository = PremiumRepository(provider, FakeStorage(License.FREE), scope) { now }

        repository.loadInitial()
        provider.connectionFlow.value = BillingConnectionState.CONNECTED
        provider.purchasesFlow.value = setOf(purchase(LicenseTier.MONTHLY, expires = now + 1000, id = "monthly"))
        provider.purchasesFlow.value = emptySet()

        assertEquals(1, provider.catalogueQueries)
    }

    /**
     * The repository half of the same rule: an install older than the trial, with its storage
     * emptied, is not a first launch however empty the storage looks.
     */
    @Test
    fun clearingDataOnAnOldInstallDoesNotHandOutAnotherTrial() = runTest {
        val storage = FakeStorage(storedLicense = null)
        val installedLongAgo = now - License.TRIAL_DURATION_MILLIS - 1
        val repository = PremiumRepository(StubProvider(), storage, scope, { installedLongAgo }) { now }

        repository.loadInitial()

        assertEquals(LicenseTier.FREE, repository.entitlements.value.license.tier)
        assertFalse(repository.entitlements.value.unlocked.contains(Feature.DLNA))
        assertNotNull("the decision has to be recorded, not retaken every launch", storage.storedLicense)
    }

    /** And a genuine first launch is unaffected by the same check. */
    @Test
    fun aTrulyNewInstallStillGetsItsTrial() = runTest {
        val storage = FakeStorage(storedLicense = null)
        val repository = PremiumRepository(StubProvider(), storage, scope, { now }) { now }

        repository.loadInitial()

        assertEquals(LicenseTier.TRIAL, repository.entitlements.value.license.tier)
    }

    /**
     * Winding the device clock back must not revive a trial that has ended. The repository records
     * the newest time it has seen and judges expiry against that, so the second launch is resolved
     * at the time of the first rather than at whatever the settings screen now claims.
     */
    @Test
    fun windingTheClockBackDoesNotReviveALapsedTrial() = runTest {
        val storage = FakeStorage(storedLicense = License.trialStartingAt(now))
        var clock = now + License.TRIAL_DURATION_MILLIS + 1

        val afterItLapsed = PremiumRepository(StubProvider(), storage, scope, { now }) { clock }
        afterItLapsed.loadInitial()
        assertTrue("the trial has to have lapsed first", afterItLapsed.entitlements.value.hasLapsed)

        clock = now - 30 * 24 * 60 * 60 * 1000L
        val afterWindingBack = PremiumRepository(StubProvider(), storage, scope, { now }) { clock }
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
}
