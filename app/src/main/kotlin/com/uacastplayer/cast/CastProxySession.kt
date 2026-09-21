package com.uacastplayer.cast

import android.content.Context
import com.uacastplayer.core.cast.CastRouteKind
import com.uacastplayer.data.cast.LocalNetworkAddress
import com.uacastplayer.data.cast.ProxyServer
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.diagnostics.RemuxEffectivenessStore
import com.uacastplayer.log.AppLog
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient

/** Sole owner of the Cast proxy's socket/resource and foreground-service lifetime.
 * Session SDK callbacks supply a token; the repository never operates the server directly. */
internal class CastProxySession(context: Context, httpClient: OkHttpClient) {
    private val appContext = context.applicationContext
    private val preferences = AppPreferences(appContext)
    private val metrics = RemuxEffectivenessStore.getInstance(appContext)
    private val attemptId = AtomicLong(0)
    private val server = ProxyServer(httpClient) { resourceId, route ->
        metrics.recordProxyRouteAttemptOnce(attemptId.get(), resourceId, route)
    }
    private var activeResourceId: String? = null
    var token: String = ""
        private set

    fun adoptToken(token: String) {
        this.token = token
    }

    fun beginPlaybackAttempt() {
        attemptId.incrementAndGet()
    }

    fun startEagerly() {
        val host = LocalNetworkAddress.currentIpv4Address(appContext) ?: return
        CastProxyOperation.run { ensureStarted(host) }.onFailure { error ->
            AppLog.w(TAG) { "Eager Cast proxy startup failed: ${error.javaClass.simpleName}" }
            stop()
        }
    }

    fun prepare(
        host: String,
        streamUrl: String,
        title: String,
        userAgent: String?,
        referrer: String?,
        receiverName: String?,
    ): Result<PreparedCastProxy> = CastProxyOperation.run {
        ensureStarted(host)
        val resourceId = server.registerPlaylist(streamUrl, userAgent, referrer)
        val prepared = PreparedCastProxy(resourceId, server.buildLocalUrl(resourceId))
        CastProxyService.start(appContext, title, receiverName.orEmpty())
        activeResourceId = resourceId
        prepared
    }.onFailure { stop() }

    fun routeKind(mode: CastDeliveryMode): CastRouteKind = when (mode) {
        CastDeliveryMode.Direct -> CastRouteKind.DIRECT
        CastDeliveryMode.Proxy -> if (activeResourceId?.let(server::wasRemuxed) == true) {
            CastRouteKind.PROXY_REMUX
        } else {
            CastRouteKind.PROXY_REWRITE
        }
    }

    fun bytesServedToReceiver(): Long = server.bytesServedToReceiver()

    fun confirmActiveSession() = server.confirmActiveSession()

    fun stop() {
        activeResourceId = null
        server.stop()
        CastProxyService.stop(appContext)
    }

    private fun ensureStarted(host: String) {
        server.ensureStarted(
            sessionToken = token,
            host = host,
            remuxEnabled = preferences.rawTsRemuxEnabled,
            unwrapWrapperPlaylists = preferences.rawTsRemuxEnabled,
        )
    }

    private companion object {
        const val TAG = "CastProxySession"
    }
}
