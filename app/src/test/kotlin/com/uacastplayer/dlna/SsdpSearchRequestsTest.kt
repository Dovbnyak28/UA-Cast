package com.uacastplayer.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SsdpSearchRequestsTest {

    private fun payloads(repeats: Int = 2) =
        SsdpSearchRequests.payloads(address = "239.255.255.250", port = 1900, mx = 2, repeats = repeats)

    /**
     * The regression guard for the change this class exists for: searching only the AVTransport
     * service type makes the device list depend on how thoroughly a particular TV implements
     * service-level M-SEARCH matching. Losing any of these three silently shrinks the set of TVs
     * the app can find, and nothing else in the app would fail.
     */
    @Test
    fun `all three search targets are asked for`() {
        // Anchored to the line start: a bare "ST: " also matches inside "HOST: ".
        val targets = payloads(repeats = 1).map { it.substringAfter("\r\nST: ").substringBefore("\r\n") }
        assertEquals(
            listOf(
                "urn:schemas-upnp-org:service:AVTransport:1",
                "urn:schemas-upnp-org:device:MediaRenderer:1",
                "ssdp:all",
            ),
            targets,
        )
    }

    /** Multicast UDP has no retransmission, so a single dropped datagram would read as "no TV
     * here" - see the class doc. */
    @Test
    fun `every target is sent more than once`() {
        assertEquals(SsdpSearchRequests.SEARCH_TARGETS.size * 3, payloads(repeats = 3).size)
        val avTransport = payloads(repeats = 3).count { it.contains("\r\nST: ${SsdpSearchRequests.AV_TRANSPORT}") }
        assertEquals(3, avTransport)
    }

    /**
     * SSDP is HTTP-shaped and devices are strict about it: headers are CRLF-separated and the block
     * ends with a blank line. A bare `\n` here would not fail any test that only checked the target
     * list, and would simply be ignored by every renderer on the network.
     */
    @Test
    fun `the datagram is CRLF-terminated and ends with a blank line`() {
        val request = SsdpSearchRequests.searchRequest(SsdpSearchRequests.ALL, "239.255.255.250", 1900, 2)
        assertTrue("must not contain a bare LF", request.split("\r\n").none { it.contains('\n') })
        assertTrue(request.endsWith("\r\n\r\n"))
        assertEquals("M-SEARCH * HTTP/1.1", request.substringBefore("\r\n"))
    }

    @Test
    fun `host and MX come from the caller rather than being hardcoded twice`() {
        val request = SsdpSearchRequests.searchRequest(SsdpSearchRequests.ALL, "239.255.255.250", 1900, 2)
        assertTrue(request.contains("HOST: 239.255.255.250:1900\r\n"))
        assertTrue(request.contains("MX: 2\r\n"))
        assertTrue(request.contains("MAN: \"ssdp:discover\"\r\n"))
    }
}
