package com.uacastplayer.dlna

import android.content.Context
import com.uacastplayer.cast.CastProxyService
import com.uacastplayer.cast.CastProxyTarget
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.data.cast.LocalNetworkAddress
import com.uacastplayer.data.cast.ProxyServer
import com.uacastplayer.log.AppLog
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

// Deliberately NOT the discovery client's few seconds - see [DlnaSessionRepository.proxyHttpClient].
// Same budget cast/CastSessionRepository gives its own ProxyServer, because it is the same job.
private const val PROXY_CONNECT_TIMEOUT_SECONDS = 10L
private const val PROXY_READ_TIMEOUT_SECONDS = 15L

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

    /**
     * The proxy's own client, NOT [discoveryHttpClient]. The discovery client's 4s read timeout is
     * sized for fetching one small device-description XML; this one is what [ProxyServer] reads an
     * *endless live stream* through, and OkHttp applies the read timeout to every read() on the
     * response body. Four seconds without a byte - routine on a congested link or through a VPN -
     * would surface as a lost connection, sending
     * [com.uacastplayer.data.cast.RawTsRemuxSession]'s reader into a backoff reconnect and a
     * discontinuity, i.e. exactly the stall-and-rebuffer symptom the proxy exists to avoid.
     */
    private val proxyHttpClient = AppHttp.client(
        connectTimeoutSeconds = PROXY_CONNECT_TIMEOUT_SECONDS,
        readTimeoutSeconds = PROXY_READ_TIMEOUT_SECONDS,
    )

    private val proxyServer = ProxyServer(proxyHttpClient)
    private val avTransportClient = AvTransportClient(soapHttpClient)
    private val ssdpDiscovery = SsdpDiscovery(appContext, discoveryHttpClient)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // One token for the whole DLNA session, not one per connect(): it is what makes the channel
    // switch below a ProxyServer.ensureStarted() no-op instead of a socket rebind. Cleared on stop()
    // and on a failed connect, both of which do tear the server down. @Volatile because connect()
    // reads and writes it from Dispatchers.IO while stop() clears it on the main thread.
    @Volatile private var sessionToken: String? = null

    // Cancelling the previous attempt is about the STATE assignment, not the in-flight SOAP call:
    // two quick taps (or a tap plus a channel switch) otherwise race, and whichever renderer replied
    // slower would win _state regardless of which one the user actually picked last.
    private var connectJob: Job? = null

    private val _state = MutableStateFlow(DlnaConnectionState())
    val state: StateFlow<DlnaConnectionState> = _state.asStateFlow()

    /** A last-resort net under [SsdpDiscovery]'s own per-stage catches - discovery failing must
     * leave the sheet with an empty list, never propagate. Cancellation is NOT a failure and is
     * rethrown (the sheet cancels this the moment it's dismissed), which is exactly what a
     * `runCatching` here would have swallowed once [SsdpDiscovery.discover] became suspending. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun discoverDevices(): List<DlnaDevice> = withContext(Dispatchers.IO) {
        try {
            ssdpDiscovery.discover()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG) { "Discovery failed: ${e.javaClass.simpleName}" }
            emptyList()
        }
    }

    /** Starts the proxy for [streamUrl] and points [device] at the resulting local url. */
    fun connect(device: DlnaDevice, streamUrl: String, title: String) {
        _state.update { it.copy(isConnecting = true) }
        connectJob?.cancel()
        connectJob = scope.launch {
            val connected = withContext(Dispatchers.IO) { connectBlocking(device, streamUrl, title) }
            _state.value = if (connected) {
                DlnaConnectionState(connectedDevice = device)
            } else {
                DlnaConnectionState()
            }
        }
    }

    /**
     * Re-points an already-connected renderer at a different channel - the DLNA counterpart of
     * `cast/CastSessionRepository.setActiveChannel`, called from `PlayerViewModel` on every channel
     * switch. A no-op when nothing is connected, which is why the player can call it
     * unconditionally.
     *
     * Without this a channel switch during a DLNA cast changed nothing on the TV: the renderer was
     * never told about the new url and kept playing the old one, while the phone (which stands down
     * for a remote target, see `LocalPlaybackPolicy`) played nothing either.
     */
    fun setActiveChannel(streamUrl: String, title: String) {
        val device = _state.value.connectedDevice ?: return
        connect(device, streamUrl, title)
    }

    private fun connectBlocking(device: DlnaDevice, streamUrl: String, title: String): Boolean {
        val host = LocalNetworkAddress.currentIpv4Address(appContext)
        if (host == null) {
            AppLog.w(TAG) { "No LAN address available; cannot start DLNA proxy" }
            return false
        }
        // The same foreground service the Chromecast path uses, not a bare wake lock. A
        // PARTIAL_WAKE_LOCK keeps the CPU awake but does nothing to stop the OS reclaiming a
        // backgrounded process - and this proxy only matters while it is *serving*, so leaving the
        // app killed the TV's stream on exactly the aggressive OEM builds CastProxyService's own
        // doc was written for. The service owns the wake/wifi locks for its own lifetime, so this
        // class no longer holds any of its own.
        CastProxyService.start(appContext, title, device.friendlyName, CastProxyTarget.DLNA)
        // ensureStarted, not start: a channel switch (see setActiveChannel) must reuse the running
        // socket and port rather than rebind a fresh one out from under a renderer that may still
        // be fetching the previous url - the contract ProxyServer.start's own doc spells out.
        val token = sessionToken ?: UUID.randomUUID().toString().also { sessionToken = it }
        proxyServer.ensureStarted(sessionToken = token, host = host)
        val localUrl = proxyServer.buildLocalUrl(proxyServer.registerPlaylist(streamUrl))
        val ok = avTransportClient.setAvTransportUri(device.controlUrl, localUrl, title) &&
            avTransportClient.play(device.controlUrl)
        if (!ok) {
            AppLog.w(TAG) { "DLNA connect failed for ${device.friendlyName}" }
            proxyServer.stop()
            CastProxyService.stop(appContext)
            sessionToken = null
        }
        return ok
    }

    /**
     * Stops playback on the connected renderer (if any) and tears down the local proxy.
     *
     * The teardown is synchronous and the SOAP `Stop` is what gets deferred, not the other way
     * round: leaving `proxyServer.stop()` behind a coroutine that first waits out a renderer's SOAP
     * timeout let a user who stopped and immediately picked another device have that pending
     * teardown land *after* the new session's [connectBlocking] had already bound a port, killing
     * the proxy the renderer was just handed. Neither call blocks here - `ProxyServer.stop` closes
     * sockets and signals its reader threads without joining them (see `RawTsRemuxSession.stop`),
     * which is the same main-thread teardown the Chromecast path performs.
     */
    fun stop() {
        val device = _state.value.connectedDevice
        _state.value = DlnaConnectionState()
        connectJob?.cancel()
        connectJob = null
        sessionToken = null
        proxyServer.stop()
        CastProxyService.stop(appContext)
        scope.launch {
            withContext(Dispatchers.IO) {
                if (device != null) avTransportClient.stop(device.controlUrl)
            }
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
