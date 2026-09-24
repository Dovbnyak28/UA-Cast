package com.uacastplayer.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class SsdpResponseParserTest {

    @Test
    fun `extracts LOCATION from a typical M-SEARCH response`() {
        val raw = "HTTP/1.1 200 OK\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "LOCATION: http://192.168.1.50:9197/dmr\r\n" +
            "SERVER: Linux/1.0 UPnP/1.0 SamsungMRDesc/1.0\r\n" +
            "ST: urn:schemas-upnp-org:service:AVTransport:1\r\n" +
            "USN: uuid:abc::urn:schemas-upnp-org:service:AVTransport:1\r\n\r\n"

        val response = SsdpResponseParser.parse(raw)

        assertEquals("http://192.168.1.50:9197/dmr", response.location)
        assertEquals("max-age=1800", response.headers["cache-control"])
    }

    @Test
    fun `header keys are matched case-insensitively`() {
        val raw = "HTTP/1.1 200 OK\r\nlocation: http://10.0.0.5:1400/desc.xml\r\n\r\n"

        val response = SsdpResponseParser.parse(raw)

        assertEquals("http://10.0.0.5:1400/desc.xml", response.location)
    }

    @Test
    fun `a response with no LOCATION header has a null location`() {
        val raw = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nST: upnp:rootdevice\r\n\r\n"

        val response = SsdpResponseParser.parse(raw)

        assertNull(response.location)
    }

    @Test
    fun `garbage input with no colon-separated lines parses to no headers`() {
        val response = SsdpResponseParser.parse("not a valid ssdp response at all")

        assertNull(response.location)
        assertEquals(0, response.headers.size)
    }

    @Test
    fun `non HTTP and relative locations are rejected before discovery capacity is consumed`() {
        for (location in listOf("file:///tmp/device.xml", "/device.xml", "http://user:pass@10.0.0.5/device")) {
            val response = SsdpResponseParser.parse("HTTP/1.1 200 OK\r\nLOCATION: $location\r\n\r\n")

            assertNull("accepted unsafe SSDP LOCATION: $location", response.location)
        }
    }

    @Test
    fun `HTTPS and a valid explicit port remain supported`() {
        val response = SsdpResponseParser.parse(
            "HTTP/1.1 200 OK\r\nLOCATION: https://tv.local:8443/device.xml\r\n\r\n",
        )

        assertEquals("https://tv.local:8443/device.xml", response.location)
    }

    @Test
    fun `port zero and out of range ports are rejected`() {
        for (location in listOf("http://10.0.0.5:0/device.xml", "http://10.0.0.5:65536/device.xml")) {
            val response = SsdpResponseParser.parse("HTTP/1.1 200 OK\r\nLOCATION: $location\r\n\r\n")
            assertNull(location, response.location)
        }
    }

    @Test
    fun `discovery location must resolve to the SSDP sender`() {
        val sender = InetAddress.getByName("192.168.1.50")
        assertEquals(
            "http://192.168.1.50:9197/device.xml",
            UpnpHttpEndpoint.discoveryLocation("http://192.168.1.50:9197/device.xml", sender),
        )
        assertNull(UpnpHttpEndpoint.discoveryLocation("http://127.0.0.1:8080/private", sender))
        assertNull(UpnpHttpEndpoint.discoveryLocation("http://192.168.1.51:9197/device.xml", sender))
    }
}
