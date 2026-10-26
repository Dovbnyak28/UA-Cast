package com.uacastplayer.player

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
class PlayerNetworkGateTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `internet behind a captive portal is not usable playback connectivity`() {
        setActiveCapabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        assertFalse(PlayerNetworkGate(application).hasValidatedNetwork())
    }

    @Test
    fun `validated internet is usable playback connectivity`() {
        setActiveCapabilities(
            NetworkCapabilities.NET_CAPABILITY_INTERNET,
            NetworkCapabilities.NET_CAPABILITY_VALIDATED,
        )

        assertTrue(PlayerNetworkGate(application).hasValidatedNetwork())
    }

    @Test
    fun `the wake-up request requires the same two capabilities as the immediate check`() {
        val request = validatedInternetRequest()

        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
    }

    private fun setActiveCapabilities(vararg capabilities: Int) {
        val manager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowOf(manager).setDefaultNetworkActive(true)
        val network = checkNotNull(manager.activeNetwork)
        val networkCapabilities = ShadowNetworkCapabilities.newInstance()
        capabilities.forEach { shadowOf(networkCapabilities).addCapability(it) }
        shadowOf(manager).setNetworkCapabilities(network, networkCapabilities)
    }
}
