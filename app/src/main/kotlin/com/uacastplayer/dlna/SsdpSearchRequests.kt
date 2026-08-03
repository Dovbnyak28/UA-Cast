package com.uacastplayer.dlna

/**
 * The M-SEARCH datagram payloads [SsdpDiscovery] sends, kept pure so the set of search targets is
 * testable without a socket.
 *
 * Two things here are the difference between finding a TV and showing an empty sheet, and neither
 * is visible from the send call:
 *
 * - **Three search targets, not one.** A renderer that speaks AVTransport is *supposed* to answer
 *   an `ST` naming that service, and many do. Plenty of real TVs only answer at the device level
 *   ([MEDIA_RENDERER]) or ignore anything but a wildcard ([ALL]) - the spec obliges a device to
 *   answer `ssdp:all` and each of its own types, but says nothing that forces the service-level
 *   match to be implemented well. Asking only the narrow question makes the app's device list
 *   depend on a detail of the TV's firmware. The extra answers cost nothing: a description that
 *   turns out to have no AVTransport service is dropped by [DeviceDescriptionParser], which is
 *   already how a non-renderer is rejected.
 * - **Each target is sent more than once.** SSDP is UDP over multicast with no retransmission of
 *   any kind, and a dropped datagram is ordinary rather than exceptional - especially over Wi-Fi,
 *   where multicast is sent at the lowest basic rate and is the first thing lost under load. One
 *   datagram per search made "the TV was busy for 40ms" indistinguishable from "there is no TV".
 */
object SsdpSearchRequests {

    const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    const val MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"
    const val ALL = "ssdp:all"

    val SEARCH_TARGETS = listOf(AV_TRANSPORT, MEDIA_RENDERER, ALL)

    /** Copies of each target's datagram, in send order. */
    fun payloads(address: String, port: Int, mx: Int, repeats: Int): List<String> =
        SEARCH_TARGETS.flatMap { target -> List(repeats) { searchRequest(target, address, port, mx) } }

    fun searchRequest(searchTarget: String, address: String, port: Int, mx: Int): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $address:$port\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: $mx\r\n" +
            "ST: $searchTarget\r\n\r\n"
}
