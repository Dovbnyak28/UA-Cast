package com.uacastplayer.dlna

import android.content.Context
import com.uacastplayer.data.cast.CastWakeLocks
import com.uacastplayer.data.cast.LocalNetworkAddress
import com.uacastplayer.data.cast.ProxyServer
import com.uacastplayer.log.AppLog
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

private const val TAG = "DlnaSessionRepository"
private const val SOAP_TIMEOUT_SECONDS = 4L
private const val DEVICE_DESCRIPTION_TIMEOUT_SECONDS = 4L

/**
 * App-wide singleton (same lifetime rationale as [com.uacastplayer.cast.CastSessionRepository]:
 * a cast connection must survive navigating away from and back to the player) for DLNA/UPnP
 * casting. Deliberately independent of the Chromecast repository - the two are mutually exclusive
 * cast targets in this MVP, so there is no shared state between them, only a shared [ProxyServer]
 * *mechanism* (each repository owns its own instance).
 *
 * Reuses the app's local HLS proxy exactly the way Chromecast does
 * ([com.uacastplayer.cast.CastSessionRepository.startProxyAndLoad]): start it, register the
 * channel's playlist url, hand the resulting local url to the renderer instead of the origin url.
 * This is deliberate, not incidental - it sidesteps the same geo/TLS/header issues Chromecast has,
 * and keeps one proxy implementation instead of two. See `docs/DLNA.md` for what this MVP does
 * and does not do (no seek/position sync, no volume, no codec gating).
 */
class DlnaSessionRepository private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val discoveryHttpClient = OkHttpClient.Builder()
        .connectTimeout(DEVICE_DESCRIPTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DEVICE_DESCRIPTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    private val soapHttpClient = OkHttpClient.Builder()
        .connectTimeout(SOAP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(SOAP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(SOAP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val proxyServer = ProxyServer(discoveryHttpClient)
    private val avTransportClient = AvTransportClient(soapHttpClient)
    private val ssdpDiscovery = SsdpDiscovery(appContext, discoveryHttpClient)
    private val wakeLocks = CastWakeLocks(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(DlnaConnectionState())
    val state: StateFlow<DlnaConnectionState> = _state.asStateFlow()

    suspend fun discoverDevices(): List<DlnaDevice> = withContext(Dispatchers.IO) {
        runCatching { ssdpDiscovery.discover() }
            .onFailure { AppLog.w(TAG) { "Discovery failed: ${it.javaClass.simpleName}" } }
            .getOrDefault(emptyList())
    }

    /** Starts the proxy for [streamUrl] and points [device] at the resulting local url. */
    fun connect(device: DlnaDevice, streamUrl: String, title: String) {
        _state.update { it.copy(isConnecting = true) }
        scope.launch {
            val connected = withContext(Dispatchers.IO) { connectBlocking(device, streamUrl, title) }
            _state.value = if (connected) {
                DlnaConnectionState(connectedDevice = device)
            } else {
                DlnaConnectionState()
            }
        }
    }

    private fun connectBlocking(device: DlnaDevice, streamUrl: String, title: String): Boolean {
        val host = LocalNetworkAddress.currentIpv4Address()
        if (host == null) {
            AppLog.w(TAG) { "No LAN address available; cannot start DLNA proxy" }
            return false
        }
        wakeLocks.acquire()
        proxyServer.start(sessionToken = UUID.randomUUID().toString(), host = host)
        val localUrl = proxyServer.buildLocalUrl(proxyServer.registerPlaylist(streamUrl))
        val ok = avTransportClient.setAvTransportUri(device.controlUrl, localUrl, title) &&
            avTransportClient.play(device.controlUrl)
        if (!ok) {
            AppLog.w(TAG) { "DLNA connect failed for ${device.friendlyName}" }
            proxyServer.stop()
            wakeLocks.release()
        }
        return ok
    }

    /** Stops playback on the connected renderer (if any) and tears down the local proxy. */
    fun stop() {
        val device = _state.value.connectedDevice
        _state.value = DlnaConnectionState()
        scope.launch {
            withContext(Dispatchers.IO) {
                if (device != null) avTransportClient.stop(device.controlUrl)
            }
            proxyServer.stop()
            wakeLocks.release()
        }
    }

    companion object {
        @Volatile private var instance: DlnaSessionRepository? = null

        fun getInstance(context: Context): DlnaSessionRepository =
            instance ?: synchronized(this) {
                instance ?: DlnaSessionRepository(context).also { instance = it }
            }
    }
}
