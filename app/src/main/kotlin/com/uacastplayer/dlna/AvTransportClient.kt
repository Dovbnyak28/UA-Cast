package com.uacastplayer.dlna

import com.uacastplayer.log.AppLog
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "AvTransportClient"

/**
 * Sends the three AVTransport SOAP actions a live-TV DLNA cast needs. [httpClient] should be
 * configured with short timeouts by the caller - a renderer that doesn't respond promptly should
 * fail fast rather than hang the UI. Envelope/DIDL-Lite building is pure and lives in
 * [AvTransportSoapBuilder]; this class is just the HTTP POST.
 */
class AvTransportClient(
    private val httpClient: OkHttpClient,
    /** Longer-timeout client for the one action a renderer answers only after fetching the url -
     * see `DlnaSessionRepository.SET_URI_TIMEOUT_SECONDS`. */
    private val setUriHttpClient: OkHttpClient = httpClient,
) {

    fun setAvTransportUri(controlUrl: String, mediaUrl: String, title: String): Boolean {
        val envelope = AvTransportSoapBuilder.setAvTransportUriEnvelope(mediaUrl, title)
        return postSoapAction(controlUrl, "SetAVTransportURI", envelope, setUriHttpClient) == SoapOutcome.OK
    }

    /**
     * Retries while the renderer answers "transition not available" ([UpnpFault.TRANSITION_NOT_AVAILABLE]).
     *
     * On a channel switch the transport is still PLAYING the previous url when the new one is set,
     * and it answers Play with 701 until it has finished moving between the two. That is a renderer
     * that is *about to work*, not one that refused - and the caller's only failure handling is to
     * tear the whole proxy down, which pulls the stream out from under a TV that had already
     * accepted the new url and started fetching it. That teardown was the error on screen.
     */
    fun play(controlUrl: String): Boolean {
        var outcome = SoapOutcome.TRANSITIONING
        var attempt = 0
        while (outcome == SoapOutcome.TRANSITIONING && attempt < PLAY_ATTEMPTS) {
            if (attempt > 0) Thread.sleep(PLAY_RETRY_DELAY_MILLIS)
            outcome = postSoapAction(controlUrl, "Play", AvTransportSoapBuilder.playEnvelope())
            attempt++
        }
        return outcome == SoapOutcome.OK
    }

    fun stop(controlUrl: String): Boolean =
        postSoapAction(controlUrl, "Stop", AvTransportSoapBuilder.stopEnvelope()) == SoapOutcome.OK

    /** Only [TRANSITIONING] is worth telling apart from a plain failure - it is the one refusal
     * that resolves on its own. See [play]. */
    private enum class SoapOutcome { OK, TRANSITIONING, FAILED }

    /** Every failure mode is the same to the caller - the renderer did not accept the action, so
     * the UI must fall back to local playback. That covers a refused/reset socket (IOException) as
     * much as a renderer answering with something OkHttp cannot even parse as a response, so
     * narrowing this would only turn one class of unreachable-renderer into a crash. */
    @Suppress("TooGenericExceptionCaught")
    private fun postSoapAction(
        controlUrl: String,
        action: String,
        envelope: String,
        client: OkHttpClient = httpClient,
    ): SoapOutcome {
        val request = Request.Builder()
            .url(controlUrl)
            .addHeader("SOAPACTION", AvTransportSoapBuilder.soapAction(action))
            .addHeader("User-Agent", DLNA_USER_AGENT)
            .post(envelope.toRequestBody(SOAP_MEDIA_TYPE))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                // A renderer that *refuses* an action answers HTTP 500 with a UPnP fault in the
                // body - no exception is thrown, so until this was here a rejected action produced
                // no log line at all and was indistinguishable from one that worked. The error code
                // is the whole diagnosis: 716 means the renderer could not fetch the URI we handed
                // it, 701 means the action was illegal from its current transport state, 705 means
                // another controller holds the transport. Reading the body is safe - a UPnP fault is
                // a small fixed document.
                if (response.isSuccessful) return@use SoapOutcome.OK
                // peekBody, not body.string(): the fault is a small document, but it comes from a
                // device on the LAN that nobody here wrote - see MAX_SOAP_RESPONSE_BYTES.
                val fault = UpnpFault.parse(response.peekBody(MAX_SOAP_RESPONSE_BYTES).string())
                AppLog.w(TAG) { "SOAP $action refused: HTTP ${response.code} $fault" }
                if (fault.isTransitionNotAvailable) SoapOutcome.TRANSITIONING else SoapOutcome.FAILED
            }
        } catch (e: Exception) {
            // The control URL is deliberately not interpolated. LogSanitizer would redact it to a
            // scheme/host/port marker anyway, so almost nothing survives into a shared diagnostics
            // report - and a DLNA session drives one renderer at a time, so the action and the
            // failure type are what actually identify the problem.
            AppLog.w(TAG) { "SOAP $action failed: ${e.javaClass.simpleName}" }
            SoapOutcome.FAILED
        }
    }

    private companion object {
        // A Samsung UE40KU6000 switching between two live HLS channels refused Play three times
        // over about 2.4s before accepting it. The budget here is deliberately well past that
        // rather than just over it: the measurement is one TV on an idle network, and the cost of
        // being wrong is asymmetric - an extra second of retrying is invisible, while running out
        // of attempts tears the proxy down under a TV that is already fetching segments, which is
        // the failure this exists to prevent. A renderer still refusing after ~5s is genuinely
        // stuck, not transitioning.
        const val PLAY_ATTEMPTS = 9
        const val PLAY_RETRY_DELAY_MILLIS = 600L
    }
}
