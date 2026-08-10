package com.uacastplayer.dlna

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What each way of losing a network does to a DLNA cast.
 *
 * The renderer is fetching from `http://<this phone>:<port>/...`, so a session is only as durable
 * as that address - and DLNA has no callback to say otherwise. Before this rule the app went on
 * showing "casting to your TV" for as long as the screen stayed open, over a stream that had died
 * the moment the address changed.
 */
class DlnaNetworkChangePolicyTest {

    private fun survives(from: String?, to: String?) = DlnaNetworkChangePolicy.sessionSurvives(from, to)

    /** The overwhelmingly common callback: something about the network changed and the address did
     * not. Wi-Fi reconnecting to the same router, a capability update, another network appearing
     * beside the one in use. Tearing a working cast down over any of those would be the worse bug. */
    @Test
    fun theSameAddressIsNotAChange() {
        assertTrue(survives("192.168.1.42", "192.168.1.42"))
    }

    /** Wi-Fi off, or out of range, and mobile data takes over. The TV is on the Wi-Fi this phone
     * just left; there is no address left that reaches it. */
    @Test
    fun losingWifiForMobileDataEndsTheSession() {
        assertFalse(survives("192.168.1.42", null))
    }

    /** Airplane mode, or everything off. Same answer, different cause. */
    @Test
    fun losingEveryNetworkEndsTheSession() {
        assertFalse(survives("192.168.1.42", null))
    }

    /**
     * The one that looks like recovery and is not: the router came back, or the phone roamed, and
     * DHCP handed out a different lease. Wi-Fi is up, the internet works, the TV is right there -
     * and the URL it is holding points at an address this phone no longer answers on.
     */
    @Test
    fun aNewLeaseFromTheSameRouterEndsTheSession() {
        assertFalse(survives("192.168.1.42", "192.168.1.57"))
    }

    /** Joining a different network entirely - a guest Wi-Fi, a phone hotspot. */
    @Test
    fun movingToAnotherNetworkEndsTheSession() {
        assertFalse(survives("192.168.1.42", "10.0.0.8"))
    }

    /**
     * Nothing has an address yet, which is every network callback that arrives while the app is
     * merely discovering renderers. There is no session to end, and ending one would mean tearing
     * down a connect that is still in flight.
     */
    @Test
    fun withNoSessionAddressThereIsNothingToEnd() {
        assertTrue(survives(null, "192.168.1.42"))
        assertTrue(survives(null, null))
    }
}
