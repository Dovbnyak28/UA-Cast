package com.uacastplayer.dlna

/**
 * A discovered UPnP/DLNA renderer.
 *
 * @param controlUrl absolute URL of the AVTransport control endpoint - the one that plays and stops.
 * @param renderingControlUrl absolute URL of the RenderingControl endpoint, or null when the
 *   renderer does not advertise that service. It is a *separate* UPnP service from AVTransport with
 *   its own control URL and its own SOAPACTION namespace, which is why it is a second field rather
 *   than another action on the first. Volume lives there; a renderer without it simply gets no
 *   volume control, which is why this is nullable rather than assumed.
 */
data class DlnaDevice(
    val friendlyName: String,
    val controlUrl: String,
    val renderingControlUrl: String? = null,
)

/**
 * One entry per renderer, keyed by the endpoint this app actually talks to.
 *
 * Discovery deduplicates the SSDP replies it receives, but it does that by `LOCATION` - the URL of
 * the *description document* - and one device routinely publishes more than one. It is asked three
 * different search targets, twice each (see [SsdpSearchRequests]), and a device is free to answer
 * each with a different description URL: `/desc.xml` for one, `/description.xml` for another, a
 * host name in one and a literal address in the next. Every one of those fetches the same
 * description and yields the same renderer, so the sheet showed the user their TV two or three
 * times, with no way to tell the entries apart because the only thing on the row is a name the
 * TV's owner typed in.
 *
 * [DlnaDevice.controlUrl] is the right identity for this and the friendly name is not: two rows
 * with the same name can genuinely be two renderers - a receiver with two zones, two identical
 * speakers out of the box - and collapsing those would hide one the user might want. Two rows with
 * the same control URL are the same endpoint, addressed the same way, and always one renderer.
 */
fun List<DlnaDevice>.distinctRenderers(): List<DlnaDevice> = distinctBy { it.controlUrl }

/** Whatever the app is currently doing with a DLNA renderer - at most one connection at a time in this MVP. */
data class DlnaConnectionState(
    val connectedDevice: DlnaDevice? = null,
    val isConnecting: Boolean = false,
    /**
     * The renderer's current volume on its own scale, or null when it is not known - either the
     * renderer advertises no RenderingControl service, or the read failed. Null is rendered as "no
     * volume control" rather than as zero, which would be a lie the user could act on.
     */
    val volume: Int? = null,
)
