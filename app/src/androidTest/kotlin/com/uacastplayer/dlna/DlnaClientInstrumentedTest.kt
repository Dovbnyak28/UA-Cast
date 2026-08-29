package com.uacastplayer.dlna

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole DLNA control stack against a renderer, on the device that has to talk to one.
 *
 * There is no TV in this test and there does not need to be. What a real renderer contributes to a
 * failure is its *answers* - a 500 carrying a UPnP fault, a `Play` refused with 701 while it is
 * still moving between two URLs, a description document with the control URL written relative -
 * and those are reproducible. What is not reproducible off-device is the rest of the path: this
 * app's OkHttp, this device's TLS-free plain sockets, this ART runtime's XML parser with the
 * hardening [DeviceDescriptionParser] applies to it.
 *
 * The renderer here answers on loopback. Discovery is deliberately not part of it - SSDP is
 * multicast, and multicast on loopback tells you about the emulator's network stack rather than
 * about this app.
 */
class DlnaClientInstrumentedTest {

    private var renderer: FakeRenderer? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @After
    fun tearDown() {
        renderer?.close()
    }

    /**
     * A UPnP MediaRenderer, as much of one as this app ever asks for: a description document and a
     * control endpoint that answers SOAP.
     *
     * [refusePlayTimes] makes it answer `Play` with a 701 fault that many times before accepting,
     * which is a real Samsung UE40KU6000 switching channels, not an invented case - see
     * [AvTransportClient.play]'s KDoc.
     */
    private class FakeRenderer(
        private val refusePlayTimes: Int = 0,
        private val failSetUriWith: String? = null,
    ) : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newCachedThreadPool()
        private val actions = mutableListOf<String>()
        private val playCalls = AtomicInteger(0)

        val port: Int get() = socket.localPort

        fun urlFor(path: String) = "http://127.0.0.1:$port$path"

        fun actionsSeen(): List<String> = synchronized(actions) { actions.toList() }

        /** The control URL is written *relative* on purpose. Real devices do it both ways, and a
         * controller that only handles absolute URLs silently finds no service at all. */
        private fun description() = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>Fake Living Room TV</friendlyName>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/upnp/control/AVTransport1</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>/upnp/control/RenderingControl1</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()

        private fun fault(code: String) = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body><s:Fault>
                <faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring>
                <detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
                  <errorCode>$code</errorCode><errorDescription>Fake refusal</errorDescription>
                </UPnPError></detail>
              </s:Fault></s:Body>
            </s:Envelope>
        """.trimIndent()

        private fun volumeResponse(volume: Int) = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:GetVolumeResponse xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                  <CurrentVolume>$volume</CurrentVolume>
                </u:GetVolumeResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        init {
            worker.execute {
                while (!socket.isClosed) {
                    try {
                        val accepted = socket.accept()
                        worker.execute { serve(accepted) }
                    } catch (_: IOException) {
                        return@execute
                    }
                }
            }
        }

        private fun serve(client: Socket) {
            client.use {
                val head = ByteArray(REQUEST_BUFFER_BYTES)
                val read = it.getInputStream().read(head)
                val request = String(head, 0, maxOf(read, 0))
                val path = request.lineSequence().firstOrNull().orEmpty().split(' ').getOrNull(1).orEmpty()
                val soapAction = Regex("(?im)^SOAPACTION:\\s*\"?([^\"\\r\\n]+)").find(request)?.groupValues?.get(1)
                val action = soapAction?.substringAfterLast('#').orEmpty()
                if (action.isNotEmpty()) synchronized(actions) { actions.add(action) }

                val (status, body) = when {
                    path == DESCRIPTION_PATH -> "200 OK" to description()
                    action == "SetAVTransportURI" && failSetUriWith != null ->
                        "500 Internal Server Error" to fault(failSetUriWith)
                    action == "Play" && playCalls.getAndIncrement() < refusePlayTimes ->
                        "500 Internal Server Error" to fault(TRANSITION_NOT_AVAILABLE)
                    action == "GetVolume" -> "200 OK" to volumeResponse(FAKE_VOLUME)
                    action.isNotEmpty() -> "200 OK" to "<?xml version=\"1.0\"?><ok/>"
                    else -> "404 Not Found" to ""
                }
                val bytes = body.toByteArray()
                val out = it.getOutputStream()
                out.write(
                    ("HTTP/1.1 $status\r\nContent-Type: text/xml\r\n" +
                        "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(),
                )
                out.write(bytes)
                out.flush()
            }
        }

        override fun close() {
            runCatching { socket.close() }
            worker.shutdownNow()
        }
    }

    private fun fetchDescription(renderer: FakeRenderer): DlnaDevice? {
        val location = renderer.urlFor(DESCRIPTION_PATH)
        val request = okhttp3.Request.Builder().url(location).build()
        return client.newCall(request).execute().use { response ->
            DeviceDescriptionParser.parse(response.body.bytes(), location)
        }
    }

    /**
     * The description document, fetched over a real socket and parsed by this device's own XML
     * stack - with both control URLs relative, which is how a controller that assumed absolute ends
     * up reporting "no renderers found" against a TV sitting right there.
     */
    @Test
    fun aDescriptionDocumentYieldsBothControlUrlsResolvedAbsolute() {
        val renderer = FakeRenderer().also { this@DlnaClientInstrumentedTest.renderer = it }

        val device = fetchDescription(renderer)

        assertNotNull("the description did not parse on this device", device)
        assertEquals("Fake Living Room TV", device!!.friendlyName)
        assertEquals(renderer.urlFor("/upnp/control/AVTransport1"), device.controlUrl)
        assertEquals(renderer.urlFor("/upnp/control/RenderingControl1"), device.renderingControlUrl)
    }

    /** The ordinary cast: hand over the URL, then start it. */
    @Test
    fun aRendererThatAcceptsIsHandedTheUrlAndThenPlayed() = runBlocking {
        val renderer = FakeRenderer().also { this@DlnaClientInstrumentedTest.renderer = it }
        val device = checkNotNull(fetchDescription(renderer))
        val transport = AvTransportClient(client)

        assertTrue(transport.setAvTransportUri(device.controlUrl, "http://127.0.0.1:1/x.ts", "Channel 1"))
        assertTrue(transport.play(device.controlUrl))
        assertTrue(transport.stop(device.controlUrl))

        assertEquals(listOf("SetAVTransportURI", "Play", "Stop"), renderer.actionsSeen())
    }

    /**
     * A `Play` refused with 701 is a renderer that is *about to work*.
     *
     * On a channel switch the transport is still playing the previous URL when the new one is set,
     * and it answers 701 until it has finished moving between them. Treating that as a failure
     * tears the proxy down under a TV that had already accepted the new URL and begun fetching it -
     * which was the error on screen. Measured on a Samsung UE40KU6000: three refusals over ~2.4s.
     *
     * Timed here as well as counted, because the retry is a real cancellable delay on a real device
     * and a budget that quietly stopped being spent would pass a call-count assertion perfectly.
     */
    @Test
    fun playRetriesThroughATransitioningRendererRatherThanGivingUp() = runBlocking {
        val renderer = FakeRenderer(refusePlayTimes = SAMSUNG_REFUSALS).also {
            this@DlnaClientInstrumentedTest.renderer = it
        }
        val device = checkNotNull(fetchDescription(renderer))

        val startedAt = System.nanoTime()
        val played = AvTransportClient(client).play(device.controlUrl)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertTrue("a transitioning renderer must not be treated as a refusal", played)
        assertEquals(SAMSUNG_REFUSALS + 1, renderer.actionsSeen().count { it == "Play" })
        assertTrue("the retries did not actually wait, got ${elapsedMillis}ms", elapsedMillis >= MIN_RETRY_MILLIS)
    }

    /**
     * 716 is the renderer saying it could not fetch the URL it was given - a real refusal, not a
     * transition, so it must fail at once rather than burn the retry budget on it. This is the
     * answer the Hisense gave the flattened-stream work, and telling the two faults apart is the
     * whole reason [UpnpFault] is parsed instead of the HTTP status alone.
    */
    @Test
    fun aRendererThatCannotFetchTheUrlFailsImmediately() = runBlocking {
        val renderer = FakeRenderer(failSetUriWith = CANNOT_FETCH_RESOURCE).also {
            this@DlnaClientInstrumentedTest.renderer = it
        }
        val device = checkNotNull(fetchDescription(renderer))

        val accepted = AvTransportClient(client).setAvTransportUri(device.controlUrl, "http://127.0.0.1:1/x.ts", "C")

        assertFalse("a 716 fault is a refusal, not a transition", accepted)
        assertEquals(1, renderer.actionsSeen().count { it == "SetAVTransportURI" })
    }

    /** Volume is a separate UPnP service with its own control URL, and null - never zero - is what
     * a renderer that cannot be read has to produce: a muted slider on a TV playing at normal
     * volume is a lie the user would act on. */
    @Test
    fun volumeIsReadFromTheRenderingControlServiceAndNullWhenUnreadable() = runBlocking {
        val renderer = FakeRenderer().also { this@DlnaClientInstrumentedTest.renderer = it }
        val device = checkNotNull(fetchDescription(renderer))
        val rendering = RenderingControlClient(client)

        assertEquals(FAKE_VOLUME, rendering.getVolume(device.renderingControlUrl!!))
        assertTrue(rendering.setVolume(device.renderingControlUrl, VOLUME_TO_SET))

        renderer.close()
        assertNull("an unreachable renderer must read as unknown, not as silent", rendering.getVolume(device.renderingControlUrl))
    }

    /**
     * A renderer that has left the network - switched off, moved to another Wi-Fi - must fail every
     * action and take nothing down with it. The socket is refused rather than timing out, which is
     * the fast half of this; the slow half is covered by the caller's own timeouts.
     */
    @Test
    fun anUnreachableRendererFailsEveryActionWithoutThrowing() = runBlocking {
        val renderer = FakeRenderer().also { this@DlnaClientInstrumentedTest.renderer = it }
        val device = checkNotNull(fetchDescription(renderer))
        renderer.close()
        val transport = AvTransportClient(client)

        assertFalse(transport.setAvTransportUri(device.controlUrl, "http://127.0.0.1:1/x.ts", "C"))
        assertFalse(transport.play(device.controlUrl))
        assertFalse(transport.stop(device.controlUrl))
    }

    private companion object {
        const val DESCRIPTION_PATH = "/description.xml"
        const val TIMEOUT_SECONDS = 10L
        const val REQUEST_BUFFER_BYTES = 8192
        const val FAKE_VOLUME = 23
        const val VOLUME_TO_SET = 40
        const val TRANSITION_NOT_AVAILABLE = "701"
        const val CANNOT_FETCH_RESOURCE = "716"

        /** What the Samsung actually did. */
        const val SAMSUNG_REFUSALS = 3

        /** Three refusals at the 600ms retry delay, minus generous slack for a loaded device. */
        const val MIN_RETRY_MILLIS = 1_500L
    }
}
