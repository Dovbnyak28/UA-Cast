package com.uacastplayer.dlna

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DlnaClientFailureBoundaryTest {

    private val httpClient = OkHttpClient()

    @Test
    fun `a malformed AVTransport control url is a refused action rather than an exception`() = runBlocking {
        val client = AvTransportClient(httpClient)

        assertFalse(client.setAvTransportUri("not a control URL", "https://example.test/live", "News"))
    }

    @Test
    fun `a malformed RenderingControl url is an unavailable volume rather than an exception`() = runBlocking {
        val client = RenderingControlClient(httpClient)

        assertNull(client.getVolume("not a control URL"))
        assertFalse(client.setVolume("not a control URL", 50))
    }
}

