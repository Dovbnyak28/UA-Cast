package com.uacastplayer.dlna

import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.core.net.executeCancellable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal enum class DlnaTransportHealth { ACTIVE, INACTIVE, UNREACHABLE, UNSUPPORTED }

/** A small, bounded status query; never opens the media URL or a second provider connection. */
internal class DlnaTransportStateReader(private val client: OkHttpClient) {
    suspend fun read(controlUrl: String): DlnaTransportHealth = runCatchingNonFatal {
        val request = Request.Builder().url(controlUrl)
            .header("SOAPACTION", AvTransportSoapBuilder.soapAction("GetTransportInfo"))
            .header("User-Agent", DLNA_USER_AGENT)
            .post(AvTransportSoapBuilder.getTransportInfoEnvelope().toRequestBody(SOAP_MEDIA_TYPE)).build()
        client.newCall(request).executeCancellable { response ->
            parse(response.code, response.peekBody(MAX_SOAP_RESPONSE_BYTES).string())
        }
    }.getOrDefault(DlnaTransportHealth.UNREACHABLE)

    companion object {
        private val successCodes = 200..299
        private val stateTag = Regex("<(?:[\\w-]+:)?CurrentTransportState[^>]*>([^<]*)</")

        internal fun parse(code: Int, body: String): DlnaTransportHealth = when {
            code !in successCodes -> if (UpnpFault.parse(body).code == "401") {
                DlnaTransportHealth.UNSUPPORTED
            } else {
                DlnaTransportHealth.UNREACHABLE
            }
            else -> when (stateTag.find(body)?.groupValues?.get(1)?.trim()?.uppercase()) {
                "PLAYING", "PAUSED_PLAYBACK", "RECORDING" -> DlnaTransportHealth.ACTIVE
                "STOPPED", "NO_MEDIA_PRESENT", "TRANSITIONING" -> DlnaTransportHealth.INACTIVE
                else -> DlnaTransportHealth.UNSUPPORTED
            }
        }
    }
}
