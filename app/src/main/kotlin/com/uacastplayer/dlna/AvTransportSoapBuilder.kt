package com.uacastplayer.dlna

private const val AV_TRANSPORT_SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1"

/**
 * `http-get:*:*:<dlna attrs>` - deliberately a wildcard MIME rather than a named one.
 *
 * The url handed to the renderer is one local proxy endpoint that serves *different* content types
 * depending on what the origin turns out to be: a rewritten HLS playlist, remuxed `video/MP2T`
 * segments, or a passthrough carrying the origin's own type (see `data/cast/ProxyServer`). Which one
 * it will be is not known when this metadata is built - the renderer has not fetched anything yet.
 * Naming a single type here was therefore a claim the proxy could not keep, and it named the HLS one:
 * renderers that use protocolInfo to decide whether to even attempt a stream would refuse a channel
 * they could have played, and the ones that cannot do HLS over DLNA at all - which is most sets that
 * are not Samsung - would refuse every channel. The HTTP `Content-Type` on the actual response is
 * what renderers play by; protocolInfo is a pre-flight filter, so the safe value is the one that
 * does not filter anything out.
 *
 * `DLNA.ORG_OP=00` says neither range nor time seeking is supported and `DLNA.ORG_FLAGS`'s leading
 * `8D500000` marks the stream as live/streaming-mode with sender pacing. Without them a renderer is
 * entitled to assume a seekable file, and a live channel that answers range requests with the head
 * of the stream is exactly the kind of thing that makes one stall a few seconds in.
 */
private const val LIVE_STREAM_PROTOCOL_INFO =
    "http-get:*:*:DLNA.ORG_OP=00;DLNA.ORG_CI=0;" +
        "DLNA.ORG_FLAGS=8D500000000000000000000000000000"

/**
 * Pure string building for the three SOAP actions this app sends to a DLNA renderer's
 * AVTransport control endpoint. No XML library needed - these are small, fixed-shape documents,
 * and the tests parse the output back to confirm it is well-formed rather than trusting the
 * templates blindly.
 *
 * `CurrentURIMetaData` carries a DIDL-Lite XML fragment as *text*, so it is XML-escaped a second
 * time on top of the DIDL-Lite fragment's own escaping - this is standard UPnP practice, not a bug.
 */
object AvTransportSoapBuilder {

    fun setAvTransportUriEnvelope(mediaUrl: String, title: String): String {
        val metadata = xmlEscape(didlLite(mediaUrl, title))
        val body = "<InstanceID>0</InstanceID>" +
            "<CurrentURI>${xmlEscape(mediaUrl)}</CurrentURI>" +
            "<CurrentURIMetaData>$metadata</CurrentURIMetaData>"
        return envelope("SetAVTransportURI", body)
    }

    fun playEnvelope(): String = envelope("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")

    fun stopEnvelope(): String = envelope("Stop", "<InstanceID>0</InstanceID>")

    fun soapAction(action: String): String = "\"$AV_TRANSPORT_SERVICE_TYPE#$action\""

    fun didlLite(mediaUrl: String, title: String): String =
        "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"0\" restricted=\"1\">" +
            "<dc:title>${xmlEscape(title)}</dc:title>" +
            // videoBroadcast, not the generic videoItem: it is the DIDL class for a live channel,
            // and it is the signal a renderer uses to lay out a "no duration, no scrub bar" UI
            // instead of showing a progress bar for a stream that has no end.
            "<upnp:class>object.item.videoItem.videoBroadcast</upnp:class>" +
            "<res protocolInfo=\"$LIVE_STREAM_PROTOCOL_INFO\">${xmlEscape(mediaUrl)}</res>" +
            "</item></DIDL-Lite>"

    private fun envelope(action: String, body: String): String =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$action xmlns:u=\"$AV_TRANSPORT_SERVICE_TYPE\">$body</u:$action></s:Body>" +
            "</s:Envelope>"

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
