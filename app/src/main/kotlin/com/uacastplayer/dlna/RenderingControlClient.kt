package com.uacastplayer.dlna

import com.uacastplayer.log.AppLog
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "RenderingControlClient"

/**
 * Volume, over the renderer's RenderingControl service.
 *
 * Its own class rather than two more methods on [AvTransportClient], because the two are different
 * UPnP services: different control URL, different SOAPACTION namespace, and a renderer may expose
 * either without the other. They also fail differently - AVTransport has to tell a "transition not
 * available" refusal apart from a real one so a channel switch can retry, while a refused volume is
 * simply a volume that did not change. Folding both into one client meant one function carrying
 * every difference as a parameter.
 *
 * The POST boilerplate below is deliberately similar to the other client's. Sharing it would mean a
 * third type parameterised over exactly the differences above, which is more machinery than fifteen
 * lines of OkHttp is worth.
 */
class RenderingControlClient(private val httpClient: OkHttpClient) {

    /**
     * The renderer's current volume, or null when it cannot be read.
     *
     * Null, never zero. A muted slider shown for a renderer playing at normal volume is a lie the
     * user would act on - they would drag it up and make the TV louder than they wanted.
     */
    fun getVolume(controlUrl: String): Int? {
        val body = post(controlUrl, "GetVolume", RenderingControlSoapBuilder.getVolumeEnvelope())
        return body?.let(VolumeResponseParser::parse)
    }

    /** Sets the volume, returning whether the renderer accepted the action. A caller that wants to
     * display the result should read it back rather than trust the value it sent - see
     * [DlnaSessionRepository.setVolume]. */
    fun setVolume(controlUrl: String, volume: Int): Boolean {
        val envelope = RenderingControlSoapBuilder.setVolumeEnvelope(VolumeRange.clamp(volume))
        return post(controlUrl, "SetVolume", envelope) != null
    }

    /**
     * Returns the response body on success, or null for every failure - a refused action, an
     * unreachable renderer, a response OkHttp cannot parse. They are one thing to both callers: the
     * volume did not change and there is nothing to show.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun post(controlUrl: String, action: String, envelope: String): String? {
        val request = Request.Builder()
            .url(controlUrl)
            .addHeader("SOAPACTION", RenderingControlSoapBuilder.soapAction(action))
            .addHeader("User-Agent", DLNA_USER_AGENT)
            .post(envelope.toRequestBody(SOAP_MEDIA_TYPE))
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                // Bounded for the same reason as the fault body in AvTransportClient - a volume
                // response is a few hundred bytes, and this one is parsed, not just logged.
                if (response.isSuccessful) {
                    response.peekBody(MAX_SOAP_RESPONSE_BYTES).string()
                } else {
                    logRefusal(action, response.code)
                }
            }
        } catch (e: Exception) {
            // The control URL is not interpolated, for the same reason as in AvTransportClient: a
            // diagnostics report keeps the action and the failure type, which is what identifies
            // the problem, and nothing that points at the user's LAN.
            AppLog.w(TAG) { "$action failed: ${e.javaClass.simpleName}" }
            null
        }
    }

    private fun logRefusal(action: String, code: Int): String? {
        AppLog.w(TAG) { "$action refused: HTTP $code" }
        return null
    }
}
