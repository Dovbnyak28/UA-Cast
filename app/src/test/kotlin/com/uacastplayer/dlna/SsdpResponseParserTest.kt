package com.uacastplayer.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
