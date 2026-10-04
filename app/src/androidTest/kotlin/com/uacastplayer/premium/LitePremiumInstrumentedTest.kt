package com.uacastplayer.premium

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.AppViewModel
import com.uacastplayer.core.security.LicenseRecordCodec
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.data.premium.FakeBillingProvider
import com.uacastplayer.data.security.LicenseIntegrity
import com.uacastplayer.premium.billing.PremiumProducts
import com.uacastplayer.premium.billing.BillingProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real ViewModel ownership and AndroidKeyStore persistence, with debug-only checkout. No money,
 * account, Activity or external stream is used. Run with the preserved-device test script. */
@RunWith(AndroidJUnit4::class)
class LitePremiumInstrumentedTest {
    private val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val preferences = AppPreferences(application)
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun startLite() {
        assertTrue("Debug-only licensing seam must exist", DeveloperMode.isAvailable)
        preferences.storedLicense = License.FREE
    }

    @After fun release(): Unit = runBlocking {
        onMain { stores.forEach { it.clear() } }
    }

    @Test fun onePurchaseUnlocksEverythingAndSurvivesViewModelReplacementOffline(): Unit = runBlocking {
        val first = newModel()
        onMain { first.applyDeveloperLicenseState("LITE"); first.refreshPremiumProducts() }
        val product = withTimeout(TIMEOUT_MILLIS) { first.premiumProducts.first { it.isNotEmpty() }.single() }
        assertEquals(PremiumProducts.LIFETIME, product.id)
        assertEquals(LicenseTier.FREE, first.entitlements.value.effectiveTier)
        assertFalse(first.featureManager.isUnlocked(Feature.DLNA))
        onMain { first.purchasePremium(product, null) }
        awaitPremium(first)
        assertEquals(Feature.entries.toSet(), first.entitlements.value.unlocked)
        assertEquals(null, preferences.storedLicense?.expiresAtMillis)
        assertSignedRecord()

        onMain { stores.first().clear() }
        val replacement = newModel() // Store is disabled in this build, so only persisted access can work.
        awaitPremium(replacement)
        assertEquals(Feature.entries.toSet(), replacement.entitlements.value.unlocked)
        assertTrue(replacement.featureManager.isUnlocked(Feature.DLNA))
        assertEquals(null, replacement.entitlements.value.license.expiresAtMillis)
    }

    @Test fun offlinePreservesPremiumButAnAuthoritativeRefundRevokesIt(): Unit = runBlocking {
        val model = newModel()
        onMain { model.applyDeveloperLicenseState("PREMIUM") }
        awaitPremium(model)
        onMain { model.applyDeveloperLicenseState("OFFLINE") }
        awaitPremium(model)
        assertEquals(Feature.entries.toSet(), model.entitlements.value.unlocked)

        onMain { model.applyDeveloperLicenseState("REFUND") }
        withTimeout(TIMEOUT_MILLIS) { model.entitlements.first { it.effectiveTier == LicenseTier.FREE } }
        assertEquals(Entitlements.FREE, model.entitlements.value)
        assertEquals(License.FREE, preferences.storedLicense)
        assertSignedRecord()
    }

    @Test fun aSignedLegacyPaidRecordMigratesWithoutChangingItsExpiry(): Unit = runBlocking {
        val legacy = License(LicenseTier.YEARLY, System.currentTimeMillis() + TIMEOUT_MILLIS, PremiumProducts.YEARLY)
        preferences.storedLicense = legacy
        assertSignedRecord()
        val model = newModel()
        awaitPremium(model)
        assertEquals(legacy.copy(tier = LicenseTier.LIFETIME), preferences.storedLicense)
        assertEquals(legacy.expiresAtMillis, model.entitlements.value.license.expiresAtMillis)
        assertSignedRecord()
    }

    @Test fun legacyExpiryPublishesLiteWithoutClosingAndReopeningTheApp(): Unit = runBlocking {
        val deadline = System.currentTimeMillis() + LEGACY_EXPIRY_MILLIS
        preferences.storedLicense = License(LicenseTier.MONTHLY, deadline, PremiumProducts.MONTHLY)
        val model = newModel()
        awaitPremium(model)
        withTimeout(TIMEOUT_MILLIS) { model.entitlements.first { it.hasLapsed } }
        assertEquals(LicenseTier.FREE, model.entitlements.value.effectiveTier)
        assertFalse(model.featureManager.isUnlocked(Feature.DLNA))
        assertEquals(deadline, preferences.storedLicense?.expiresAtMillis)
        assertSignedRecord()
    }

    @Test fun clearingTheRealViewModelStoreReleasesItsBillingProviderOnce(): Unit = runBlocking {
        var releases = 0
        val provider = object : BillingProvider by FakeBillingProvider() {
            override fun close() { releases++ }
        }
        val model = newModel()
        onMain {
            val previous = DeveloperMode.apply
            try {
                DeveloperMode.apply = { _, _ -> provider }
                model.applyDeveloperLicenseState("LITE")
            } finally { DeveloperMode.apply = previous }
            stores.single().clear()
            stores.single().clear()
        }
        assertEquals(1, releases)
        assertEquals(License.FREE, preferences.storedLicense)
    }

    private suspend fun newModel(): AppViewModel = onMain {
        AppViewModel(application).also { model -> stores += ViewModelStore().also { it.put("app", model) } }
    }

    private suspend fun awaitPremium(model: AppViewModel) {
        withTimeout(TIMEOUT_MILLIS) { model.entitlements.first { it.effectiveTier == LicenseTier.LIFETIME } }
    }

    private fun assertSignedRecord() {
        assertTrue("AndroidKeyStore must tag the native fixture", LicenseIntegrity.isAvailable())
        val record = application.getSharedPreferences("uacast_prefs", Application.MODE_PRIVATE)
            .getString("license_record", null)
        assertNotNull(record)
        val decoded = checkNotNull(LicenseRecordCodec.decode(checkNotNull(record)))
        assertTrue("Licence must retain a valid MAC after migration/purchase", LicenseIntegrity.verify(decoded.first, decoded.second))
    }

    private suspend fun <T> onMain(action: () -> T): T = withContext(Dispatchers.Main.immediate) { action() }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val LEGACY_EXPIRY_MILLIS = 5000L
    }
}
