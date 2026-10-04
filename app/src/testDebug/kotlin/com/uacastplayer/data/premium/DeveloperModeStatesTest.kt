package com.uacastplayer.data.premium

import com.uacastplayer.premium.Entitlements
import com.uacastplayer.premium.Feature
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseStorage
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.premium.billing.BillingConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The five states the developer menu offers, checked through the real repository rather than by
 * reading the provider's fields - what matters is the entitlement a tester ends up looking at, not
 * what the stub reports on the way there.
 *
 * These live in `src/testDebug` rather than `src/test`, and the difference is not tidiness: the
 * shared test source set is compiled for the release unit-test variant too, where
 * [DeveloperModeBillingProvider] does not exist - `src/debug` is not part of that variant. Putting
 * them here is the same boundary the production code relies on, applied to its tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeveloperModeStatesTest {

    private class FakeStorage(
        override var storedLicense: License? = null,
        override var clockHighWaterMark: Long = 0L,
    ) : LicenseStorage

    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    @After
    fun tearDown() = scope.cancel()

    /** Drives the app into [state] exactly as the Settings chip does, and returns what the user
     * would then be entitled to. */
    private fun entitlementsFor(state: String): Pair<Entitlements, PremiumRepository> {
        val storage = FakeStorage()
        val provider = DeveloperModeBillingProvider.apply(state, storage)
        val repository = PremiumRepository(provider, storage, scope)
        repository.loadInitial()
        return repository.entitlements.value to repository
    }

    @Test
    fun everyOfferedStateIsRecognised() {
        assertEquals(
            listOf("LITE", "PREMIUM", "REFUND", "OFFLINE", "EXPIRED"),
            DeveloperModeBillingProvider.STATES,
        )
    }

    @Test
    fun liteLocksThePaidFeaturesAndKeepsTheFreeOnes() = runTest {
        val (entitlements, _) = entitlementsFor("LITE")

        assertEquals(LicenseTier.FREE, entitlements.license.tier)
        assertFalse(entitlements.unlocked.contains(Feature.DLNA))
        assertTrue(entitlements.unlocked.contains(Feature.CHROMECAST))
    }

    @Test
    fun retiredDeveloperStatesCannotGrantPremium() = runTest {
        for (state in listOf("FREE", "TRIAL", "LIFETIME")) {
            val (entitlements, _) = entitlementsFor(state)
            assertEquals(state, Entitlements.FREE, entitlements)
        }
    }

    @Test
    fun premiumUnlocksEveryFeatureWithOneNonExpiringPurchase() = runTest {
        val (entitlements, _) = entitlementsFor("PREMIUM")

        assertEquals(LicenseTier.LIFETIME, entitlements.license.tier)
        assertEquals(null, entitlements.license.expiresAtMillis)
        assertEquals(Feature.entries.toSet(), entitlements.unlocked)
        assertFalse(entitlements.hasLapsed)
    }

    @Test
    fun developerCatalogueOffersOnlyThePremiumOneTimePurchase() = runTest {
        val provider = DeveloperModeBillingProvider()
        val product = provider.products().single()
        assertEquals(com.uacastplayer.premium.billing.PremiumProducts.LIFETIME, product.id)
        assertEquals(LicenseTier.LIFETIME, product.tier)
    }

    /** The state a boolean would lose: access is gone, but the app still knows this person paid
     * once and can say "ended" rather than showing them the same screen as someone who never did. */
    @Test
    fun expiredLegacyAccessLocksFeaturesWithoutLosingItsHistory() = runTest {
        val (entitlements, _) = entitlementsFor("EXPIRED")

        assertTrue(entitlements.hasLapsed)
        assertEquals(LicenseTier.LIFETIME, entitlements.license.tier)
        assertEquals(LicenseTier.FREE, entitlements.effectiveTier)
        assertFalse(entitlements.unlocked.contains(Feature.DLNA))
    }

    /** A connected store that lists nothing, against a device holding a paid license - the case
     * that must actually revoke. */
    @Test
    fun refundRevokesAPaidLicense() = runTest {
        val storage = FakeStorage()
        val provider = DeveloperModeBillingProvider.apply("REFUND", storage)
        val repository = PremiumRepository(provider, storage, scope)
        repository.loadInitial()

        assertEquals(BillingConnectionState.CONNECTED, repository.connection.value)
        assertEquals(LicenseTier.FREE, repository.entitlements.value.license.tier)
        assertFalse(repository.entitlements.value.unlocked.contains(Feature.DLNA))
    }

    /**
     * The same empty store as REFUND, with one difference: it is unreachable. Access must survive.
     * If these two ever produce the same result, the offline fallback has stopped working and a
     * paying user loses their features on an aeroplane.
     */
    @Test
    fun offlineKeepsAPaidLicenseThatRefundWouldHaveRevoked() = runTest {
        val storage = FakeStorage()
        val provider = DeveloperModeBillingProvider.apply("OFFLINE", storage)
        val repository = PremiumRepository(provider, storage, scope)
        repository.loadInitial()

        assertEquals(BillingConnectionState.UNAVAILABLE, repository.connection.value)
        assertEquals(LicenseTier.LIFETIME, repository.entitlements.value.license.tier)
        assertTrue(repository.entitlements.value.unlocked.contains(Feature.DLNA))
    }

    @Test
    fun anUnknownStateFallsBackToFreeRatherThanGrantingAnything() = runTest {
        val (entitlements, _) = entitlementsFor("something-else")

        assertEquals(LicenseTier.FREE, entitlements.license.tier)
        assertFalse(entitlements.unlocked.contains(Feature.DLNA))
    }
}
