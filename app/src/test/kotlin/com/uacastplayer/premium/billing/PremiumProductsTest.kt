package com.uacastplayer.premium.billing

import com.android.billingclient.api.BillingClient
import com.uacastplayer.premium.LicenseTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue, asserted - because the way this fails in production is silence.
 *
 * A product id that does not exist in Play Console is not an error: Play answers the query without
 * it, the price never appears, and the buy button does nothing at all. Nothing is logged by the
 * store, nothing crashes, and the app looks like it simply has no premium. These tests cannot check
 * the console, but they can hold the ids still, so that a rename has to be a deliberate act in two
 * places rather than an accident in one.
 */
class PremiumProductsTest {

    /**
     * The one claim [PremiumProducts] makes about the billing library without importing it: that
     * its own type strings are Play's. Cheap to assert, and the alternative - a query built with
     * "subscription" instead of "subs" - returns an empty list rather than an error.
     */
    @Test
    fun theProductTypeConstantsAreTheOnesPlayUses() {
        assertEquals(BillingClient.ProductType.SUBS, PremiumProducts.TYPE_SUBSCRIPTION)
        assertEquals(BillingClient.ProductType.INAPP, PremiumProducts.TYPE_ONE_TIME)
    }

    /** Renaming a live product orphans every purchase already made against the old id. These are
     * the strings that must exist in Play Console, spelled exactly this way. */
    @Test
    fun theProductIdsAreTheOnesPlayConsoleMustDefine() {
        assertEquals("premium_monthly", PremiumProducts.MONTHLY)
        assertEquals("premium_yearly", PremiumProducts.YEARLY)
        assertEquals("premium_lifetime", PremiumProducts.LIFETIME)
    }

    @Test
    fun eachProductGrantsItsOwnTier() {
        assertEquals(LicenseTier.MONTHLY, PremiumProducts.tierFor(PremiumProducts.MONTHLY))
        assertEquals(LicenseTier.YEARLY, PremiumProducts.tierFor(PremiumProducts.YEARLY))
        assertEquals(LicenseTier.LIFETIME, PremiumProducts.tierFor(PremiumProducts.LIFETIME))
    }

    /**
     * The case that decides whether a mistake gives the app away or takes it back: an id this build
     * does not know. It has to be answerable as "no idea", not as a tier.
     */
    @Test
    fun anUnknownProductGrantsNothing() {
        assertNull(PremiumProducts.tierFor("premium_weekly"))
        assertNull(PremiumProducts.tierFor(""))
        assertNull(PremiumProducts.tierFor("premium_monthly "))
    }

    /**
     * Lifetime access is a one-time purchase, not a subscription. Creating it as a subscription in
     * the console would leave it queried from the wrong catalogue and owned in the wrong one, so
     * this is as much a note to whoever fills in Play Console as it is a test.
     */
    @Test
    fun lifetimeIsAOneTimePurchaseAndTheRestAreSubscriptions() {
        assertEquals(PremiumProducts.TYPE_ONE_TIME, PremiumProducts.ALL[PremiumProducts.LIFETIME])
        assertEquals(PremiumProducts.TYPE_SUBSCRIPTION, PremiumProducts.ALL[PremiumProducts.MONTHLY])
        assertEquals(PremiumProducts.TYPE_SUBSCRIPTION, PremiumProducts.ALL[PremiumProducts.YEARLY])
    }

    /** The two query lists together must be the whole catalogue: an id in neither is never asked
     * about, and would be unbuyable without anything saying so. */
    @Test
    fun everyProductIsInExactlyOneQueryList() {
        val queried = PremiumProducts.SUBSCRIPTION_IDS + PremiumProducts.ONE_TIME_IDS

        assertEquals(PremiumProducts.ALL.keys, queried.toSet())
        assertEquals("no id may be queried twice", queried.size, queried.toSet().size)
    }

    /** Every sellable tier has a product, or it cannot be bought however good the paywall is. */
    @Test
    fun everyPaidTierCanActuallyBeBought() {
        val sold = PremiumProducts.ALL.keys.mapNotNull(PremiumProducts::tierFor).toSet()

        for (tier in LicenseTier.entries.filter { it.isPaid }) {
            assertTrue("$tier has no product to buy it with", tier in sold)
        }
    }
}
