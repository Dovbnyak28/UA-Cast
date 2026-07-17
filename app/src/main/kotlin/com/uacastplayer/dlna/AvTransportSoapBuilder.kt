package com.uacastplayer.dlna

private const val AV_TRANSPORT_SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1"

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
            "<upnp:class>object.item.videoItem</upnp:class>" +
            "<res protocolInfo=\"http-get:*:application/vnd.apple.mpegurl:*\">${xmlEscape(mediaUrl)}</res>" +
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
