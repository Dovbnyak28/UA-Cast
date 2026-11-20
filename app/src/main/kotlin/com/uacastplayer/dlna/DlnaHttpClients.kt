package com.uacastplayer.dlna

import com.uacastplayer.core.net.AppHttp
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

private const val SOAP_TIMEOUT_SECONDS = 4L

/**
 * `SetAVTransportURI` gets its own, much longer budget than [SOAP_TIMEOUT_SECONDS].
 *
 * Play and Stop are pure state changes and a renderer that cannot answer them in a few seconds is
 * not going to. SetAVTransportURI is not like that: renderers commonly *fetch the url inside the
 * action* to validate it before replying - a Samsung UE40KU6000 answers 716 "Resource not found"
 * for a dead url, which it can only know by having tried. So this action's reply is gated on the
 * proxy's first upstream round trip to the origin, and sharing the short timeout meant a slow
 * origin surfaced as a socket timeout and a failed connect on a TV that was working fine.
 */
private const val SET_URI_TIMEOUT_SECONDS = 20L

private const val DEVICE_DESCRIPTION_TIMEOUT_SECONDS = 4L

// Deliberately NOT the discovery client's few seconds - see [DlnaHttpClients.proxy].
// Same budget cast/CastSessionRepository gives its own ProxyServer, because it is the same job.
private const val PROXY_CONNECT_TIMEOUT_SECONDS = 10L
private const val PROXY_READ_TIMEOUT_SECONDS = 15L

/** The OkHttp clients [DlnaSessionRepository] builds, each sized for one kind of traffic. */
internal object DlnaHttpClients {

    fun soap(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(SOAP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(SOAP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(SOAP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * The proxy's own client, NOT [discovery]. The discovery client's 4s read timeout is sized for
     * fetching one small device-description XML; this one is what the ProxyServer reads an
     * *endless live stream* through, and OkHttp applies the read timeout to every read() on the
     * response body. Four seconds without a byte - routine on a congested link or through a VPN -
     * would surface as a lost connection, sending
     * [com.uacastplayer.data.cast.RawTsRemuxSession]'s reader into a backoff reconnect and a
     * discontinuity, i.e. exactly the stall-and-rebuffer symptom the proxy exists to avoid.
     */
    fun proxy(): OkHttpClient = AppHttp.client(
        connectTimeoutSeconds = PROXY_CONNECT_TIMEOUT_SECONDS,
        readTimeoutSeconds = PROXY_READ_TIMEOUT_SECONDS,
    )

    fun discovery(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(DEVICE_DESCRIPTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DEVICE_DESCRIPTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(DEVICE_DESCRIPTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** Shares the connection pool and dispatcher with [soap] (newBuilder does), so the second
     * client costs nothing beyond its own timeout settings. */
    fun setUri(soap: OkHttpClient): OkHttpClient = soap.newBuilder()
        .readTimeout(SET_URI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(SET_URI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}
