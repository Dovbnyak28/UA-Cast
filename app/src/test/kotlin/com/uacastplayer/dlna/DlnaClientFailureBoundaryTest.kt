package com.uacastplayer.dlna

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaClientFailureBoundaryTest {

    @Test fun `a qualified transition fault is retried before giving up on the renderer`() = runBlocking {
        val attempts = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val first = attempts.incrementAndGet() == 1
            val body = if (first) """<u:UPnPError xmlns:u="urn:schemas-upnp-org:control-1-0">
                <u:errorCode>701</u:errorCode></u:UPnPError>""" else ""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (first) 500 else 200).message("fixture").body(body.toResponseBody()).build()
        }.build()
        try {
            assertTrue(AvTransportClient(client).play("https://renderer.example/control"))
            assertEquals(2, attempts.get())
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

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

