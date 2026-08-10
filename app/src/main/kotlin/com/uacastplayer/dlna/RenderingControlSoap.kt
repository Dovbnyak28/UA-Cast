package com.uacastplayer.dlna

const val RENDERING_CONTROL_SERVICE_TYPE = "urn:schemas-upnp-org:service:RenderingControl:1"

/**
 * The two RenderingControl actions this app needs, plus the one number it has to read back out of a
 * response.
 *
 * Separate from [AvTransportSoapBuilder] because RenderingControl is a separate UPnP service: its
 * own control URL, its own SOAPACTION namespace, and a renderer is free to expose one without the
 * other. Sending a volume action to the AVTransport endpoint gets a fault, not a volume change.
 *
 * `Channel=Master` on both: UPnP allows per-channel volume (LF, RF, and so on), and every renderer
 * that implements the service at all implements Master. Asking for a channel a device does not have
 * is a fault, so the one channel that is always there is the only one worth sending.
 */
object RenderingControlSoapBuilder {

    fun getVolumeEnvelope(): String =
        envelope("GetVolume", "<InstanceID>0</InstanceID><Channel>Master</Channel>")

    fun setVolumeEnvelope(volume: Int): String = envelope(
        "SetVolume",
        "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>$volume</DesiredVolume>",
    )

    fun soapAction(action: String): String = "\"$RENDERING_CONTROL_SERVICE_TYPE#$action\""

    private fun envelope(action: String, body: String): String =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body>" +
            "<u:$action xmlns:u=\"$RENDERING_CONTROL_SERVICE_TYPE\">" +
            body +
            "</u:$action>" +
            "</s:Body>" +
            "</s:Envelope>"
}

/**
 * Reads `CurrentVolume` out of a `GetVolume` response.
 *
 * Deliberately a substring scan rather than an XML parse. The response is a fixed-shape document
 * whose one interesting element is never namespaced or attributed, renderers vary in how much
 * whitespace and how many namespace declarations they wrap it in, and a parser failing on some
 * vendor's malformed envelope would lose a number that is plainly visible in the bytes.
 *
 * Returns null for anything that is not a plain non-negative integer, which is what a fault, an
 * empty body or a truncated response all look like.
 */
object VolumeResponseParser {

    private const val OPEN = "<CurrentVolume>"
    private const val CLOSE = "</CurrentVolume>"

    fun parse(responseBody: String): Int? {
        val start = responseBody.indexOf(OPEN)
        val valueStart = if (start < 0) -1 else start + OPEN.length
        val end = if (valueStart < 0) -1 else responseBody.indexOf(CLOSE, valueStart)
        if (end < 0) return null
        val raw = responseBody.substring(valueStart, end).trim()
        // toIntOrNull would accept a leading '-' or '+'; a volume is neither.
        return if (raw.isNotEmpty() && raw.all { it.isDigit() }) raw.toIntOrNull() else null
    }
}

/**
 * The volume scale this app assumes.
 *
 * UPnP publishes each renderer's real range in its service description, and fetching that would be
 * a second HTTP round trip on connect for one number. 0-100 is what the overwhelming majority of
 * renderers use, including the Samsung set this was built against - so the slider works on those,
 * and on a renderer with a smaller range a value above its maximum is refused by the renderer
 * rather than silently mis-set. That is the honest failure: nothing moves, and the read-back shows
 * what actually happened.
 */
object VolumeRange {
    const val MIN = 0
    const val MAX = 100

    fun clamp(volume: Int): Int = volume.coerceIn(MIN, MAX)
}
