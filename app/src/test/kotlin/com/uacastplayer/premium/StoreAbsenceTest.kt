package com.uacastplayer.premium

import com.uacastplayer.premium.billing.BillingConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which sentence a user reads when there is nothing to buy.
 *
 * One sentence used to cover every case - *"this app is not published in a store"* - and it stops
 * being true the day it is published. The reader it would then be wrong for is somebody on an
 * Android TV box, which for an IPTV player is not a fringe device.
 */
class StoreAbsenceTest {

    private fun absence(
        live: Boolean,
        connection: BillingConnectionState = BillingConnectionState.DISCONNECTED,
        hasProducts: Boolean = false,
    ) = StoreAbsence.of(live, connection, hasProducts)

    /** With prices on screen there is no question to answer. */
    @Test
    fun aCatalogueEndsTheQuestion() {
        assertNull(absence(live = true, connection = BillingConnectionState.CONNECTED, hasProducts = true))
        assertNull(absence(live = false, hasProducts = true))
    }

    /**
     * Before release, every copy is in this state - including on a phone that has Google Play and
     * would happily sell something the moment there is something to sell.
     */
    @Test
    fun aBuildWithNoStoreSaysSoRatherThanBlamingTheDevice() {
        assertEquals(StoreAbsence.BUILD_HAS_NO_STORE, absence(live = false))
        assertEquals(
            "even where Play is present and answering",
            StoreAbsence.BUILD_HAS_NO_STORE,
            absence(live = false, connection = BillingConnectionState.CONNECTED),
        )
    }

    /**
     * The case this exists for. `FakeBillingProvider` also reports UNAVAILABLE, so reading the
     * connection alone would have told every pre-release user their device has no Google Play -
     * the same false sentence pointed the other way.
     */
    @Test
    fun aDeviceWithNoPlayIsToldItIsTheDeviceAndNotTheApp() {
        assertEquals(
            StoreAbsence.DEVICE_HAS_NO_STORE,
            absence(live = true, connection = BillingConnectionState.UNAVAILABLE),
        )
    }

    /**
     * A console that is not ready: a draft product, one id misspelled, a release live before its
     * track went out. The user can do nothing about this one either, but somebody can - which is
     * why it is worth telling apart from a device that will never have a store.
     */
    @Test
    fun aStoreThatAnsweredWithNothingIsItsOwnCase() {
        assertEquals(
            StoreAbsence.STORE_OFFERS_NOTHING,
            absence(live = true, connection = BillingConnectionState.CONNECTED),
        )
    }

    /** Still connecting, or briefly offline, is not "this device has no Play" - it is the same
     * not-ready case, and saying anything stronger would be guessing. */
    @Test
    fun aStoreStillBeingReachedIsNotACapabilityJudgement() {
        assertEquals(
            StoreAbsence.STORE_OFFERS_NOTHING,
            absence(live = true, connection = BillingConnectionState.CONNECTING),
        )
        assertEquals(
            StoreAbsence.STORE_OFFERS_NOTHING,
            absence(live = true, connection = BillingConnectionState.DISCONNECTED),
        )
    }
}
