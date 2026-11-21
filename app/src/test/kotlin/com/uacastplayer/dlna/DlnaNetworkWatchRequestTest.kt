package com.uacastplayer.dlna

import android.net.NetworkCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
class DlnaNetworkWatchRequestTest {

    @Test
    fun `local-only Wi-Fi must be visible to the session watcher`() {
        val request = dlnaSessionNetworkRequest()
        assertFalse(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        assertTrue(request.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
        val localOnlyWifi = ShadowNetworkCapabilities.newInstance()
        shadowOf(localOnlyWifi).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        assertFalse(localOnlyWifi.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        assertTrue(request.canBeSatisfiedBy(localOnlyWifi))
        val cellular = ShadowNetworkCapabilities.newInstance()
        shadowOf(cellular).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        assertFalse(request.canBeSatisfiedBy(cellular))
    }
}
