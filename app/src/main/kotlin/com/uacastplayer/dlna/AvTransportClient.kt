package com.uacastplayer.dlna

import com.uacastplayer.log.AppLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "AvTransportClient"
private val SOAP_MEDIA_TYPE = "text/xml; charset=\"utf-8\"".toMediaType()

/**
 * Sends the three AVTransport SOAP actions a live-TV DLNA cast needs. [httpClient] should be
 * configured with short timeouts by the caller - a renderer that doesn't respond promptly should
 * fail fast rather than hang the UI. Envelope/DIDL-Lite building is pure and lives in
 * [AvTransportSoapBuilder]; this class is just the HTTP POST.
 */
class AvTransportClient(private val httpClient: OkHttpClient) {

    fun setAvTransportUri(controlUrl: String, mediaUrl: String, title: String): Boolean =
        postSoapAction(controlUrl, "SetAVTransportURI", AvTransportSoapBuilder.setAvTransportUriEnvelope(mediaUrl, title))

    fun play(controlUrl: String): Boolean =
        postSoapAction(controlUrl, "Play", AvTransportSoapBuilder.playEnvelope())

    fun stop(controlUrl: String): Boolean =
        postSoapAction(controlUrl, "Stop", AvTransportSoapBuilder.stopEnvelope())

    private fun postSoapAction(controlUrl: String, action: String, envelope: String): Boolean {
        val request = Request.Builder()
            .url(controlUrl)
            .addHeader("SOAPACTION", AvTransportSoapBuilder.soapAction(action))
            .post(envelope.toRequestBody(SOAP_MEDIA_TYPE))
            .build()
        return try {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            AppLog.w(TAG) { "SOAP $action failed for $controlUrl: ${e.javaClass.simpleName}" }
            false
        }
    }
}
