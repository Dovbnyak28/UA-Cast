package com.uacastplayer.dlna

/**
 * The `errorCode`/`errorDescription` out of a bounded UPnP SOAP fault body, for retry policy and logs.
 *
 * A renderer that refuses an action answers HTTP 500 with a fault document rather than throwing
 * anything at the client, so without pulling the code out of it every refusal looks the same from
 * the app's side. Which code it is decides what to do about it, and the ones this app actually
 * meets are worth naming:
 *
 * - **716 Resource not found** - the renderer tried to fetch the URI it was handed and could not.
 *   Samsung sets the URI *and validates it synchronously*, so this arrives from
 *   `SetAVTransportURI` itself, not later from playback.
 * - **701 Transition not available** - the action is illegal from the transport's current state.
 *   Typically the follow-on failure after a `SetAVTransportURI` that was itself refused.
 * - **705 Transport is locked** - another controller owns the transport.
 *
 * Field parsing shares the hardened SOAP reader: prefixes, attributes and XML text representations
 * do not change the error code. A malformed body still degrades to "no detail", never an exception.
 */
internal data class UpnpFault(val code: String?, val description: String?) {

    /**
     * The renderer is mid-transition, not broken - retrying the same action shortly after is the
     * correct response, where treating it as a failure tears down a session that was about to work.
     */
    val isTransitionNotAvailable: Boolean get() = code == TRANSITION_NOT_AVAILABLE

    override fun toString(): String = listOfNotNull(
        code?.let { "errorCode=$it" },
        description?.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
    ).joinToString(" ").ifEmpty { "no UPnP fault detail" }

    companion object {
        /** "Transition not available" - see [isTransitionNotAvailable]. */
        const val TRANSITION_NOT_AVAILABLE = "701"

        fun parse(body: String): UpnpFault {
            val fields = SoapResponseFields.read(body, "errorCode", "errorDescription")
            return UpnpFault(fields["errorCode"], fields["errorDescription"])
        }
    }
}
