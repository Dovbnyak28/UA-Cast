package com.uacastplayer.dlna

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.uacastplayer.cast.CastProxyService
import com.uacastplayer.cast.CastProxyTarget
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.data.cast.LocalNetworkAddress
import com.uacastplayer.data.cast.ProxyServer
import com.uacastplayer.diagnostics.CorrelationId
import com.uacastplayer.log.AppLog
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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

// Deliberately NOT the discovery client's few seconds - see [DlnaSessionRepository.proxyHttpClient].
// Same budget cast/CastSessionRepository gives its own ProxyServer, because it is the same job.
private const val PROXY_CONNECT_TIMEOUT_SECONDS = 10L
private const val PROXY_READ_TIMEOUT_SECONDS = 15L

private data class PreparedDlnaProxy(val token: String, val localUrl: String)
private data class DlnaConnectRequest(
    val device: DlnaDevice,
    val streamUrl: String,
    val title: String,
    val isRepoint: Boolean,
    val generation: Long,
)

/**
 * App-wide singleton (same lifetime rationale as [com.uacastplayer.cast.CastSessionRepository]:
 * a cast connection must survive navigating away from and back to the player) for DLNA/UPnP
 * casting. Deliberately independent of the Chromecast repository - there is no shared state between
 * them, only a shared [ProxyServer] *mechanism* (each repository owns its own instance).
 *
 * Independent is not the same as exclusive. Both were measured connected at once, each feeding its
 * own renderer through its own proxy, and neither disturbed the other. What is absent is
 * coordination: see `player/LocalPlaybackPolicy.shouldResumeAfterDisconnect`, which has to know
 * about both targets because disconnecting one used to resume the phone underneath the other.
 *
 * Reuses the app's local HLS proxy exactly the way Chromecast does
 * ([com.uacastplayer.cast.CastSessionRepository.startProxyAndLoad]): start it, register the
 * channel's playlist url, hand the resulting local url to the renderer instead of the origin url.
 * This is deliberate, not incidental - it sidesteps the same geo/TLS/header issues Chromecast has,
 * and keeps one proxy implementation instead of two. See `docs/DLNA.md` for what this MVP does
 * and does not do (no seek/position sync, no codec gating). Volume is supported, through the
 * renderer's separate RenderingControl service - see [setVolume].
 */
class DlnaSessionRepository private constructor(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
) {

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
    private val setUriHttpClient = soapHttpClient.newBuilder()
        .readTimeout(SET_URI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(SET_URI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    // Shares the connection pool and dispatcher with soapHttpClient (newBuilder does), so the
    // second client costs nothing beyond its own timeout settings.
    private val avTransportClient = AvTransportClient(soapHttpClient, setUriHttpClient)
    // Shares soapHttpClient's connection pool and timeouts: volume is a small, fast action against
    // the same host the transport actions already talk to.
    private val renderingControlClient = RenderingControlClient(soapHttpClient)
    private val ssdpDiscovery = SsdpDiscovery(appContext, discoveryHttpClient, ioDispatcher)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectAttempts = LatestDlnaAttemptSerializer()
    private val volumeAttempts = LatestDlnaAttemptSerializer()

    // One token for the whole DLNA session, not one per connect(): it is what makes the channel
    // switch below a ProxyServer.ensureStarted() no-op instead of a socket rebind. Cleared on stop()
    // and on a failed connect, both of which do tear the server down. Every access is protected by
    // [LatestDlnaAttemptSerializer.withLifecycle], since connect runs on IO and stop on main.
    private var sessionToken: String? = null

    // Cancelling the previous attempt retires its STATE assignment immediately. Its synchronous
    // SOAP call can still be blocked, so [connectAttempts] also serializes the actual renderer
    // commands and checks cancellation between them; see [connectGeneration].
    private var connectJob: Job? = null

    /**
     * Which connect attempt is the current one. Bumped by every [startSession] and by [stop], and
     * captured by the attempt the bump started.
     *
     * [connectBlocking] runs on `Dispatchers.IO` through blocking OkHttp calls and `Thread.sleep`,
     * and cancellation interrupts none of them: a superseded attempt runs to completion regardless
     * of [connectJob], reaching its own failure teardown minutes-old. Without this counter that
     * teardown belonged to nobody - a first connect the user superseded by tapping again went on to
     * call `proxyServer.stop()` and clear [sessionToken] underneath the attempt that had since
     * succeeded, killing a cast that was working. `cast/CastSessionRepository` guards the same class
     * of race with its `loadGeneration`/`StaleChannelGuard` checks; this is the DLNA counterpart.
     */
    private val connectGeneration = AtomicLong(0)

    /**
     * The [connectGeneration] idea applied to volume - see [setVolume] for why it is needed.
     *
     * Bumped by [startSession] and [stop] as well as by each [setVolume], for the same reason
     * [connectGeneration] is: a session ending or a new one starting retires every answer still in
     * flight for the old one. Without those two, the "still connected" half of [setVolume]'s guard
     * did not mean what it says - `connectedDevice` is non-null again the moment the user connects
     * to the next renderer, so a volume read belonging to the session they just stopped could land
     * on the one they just started and sit there until the next drag.
     */
    private val volumeGeneration = AtomicLong(0)

    private val _state = MutableStateFlow(DlnaConnectionState())
    val state: StateFlow<DlnaConnectionState> = _state.asStateFlow()

    /** The address baked into the URL the renderer is fetching from - see [DlnaNetworkChangePolicy]
     * for why a session is only as durable as this. Written on connect, cleared on [stop]. */
    @Volatile private var sessionHost: String? = null

    /** Unregisters [watchForNetworkChange]'s callback; held only while a session is up. */
    private var networkWatch: AutoCloseable? = null

    /**
     * Ends the session when this phone stops being reachable at the address the renderer is using.
     *
     * DLNA has no way to tell the app that its stream died - see [DlnaNetworkChangePolicy] - so
     * without this the app kept claiming to cast to a TV that had been staring at a dead URL since
     * the moment Wi-Fi handed over to mobile data. Ending it is the honest outcome and the only one
     * available: re-pointing the renderer at the new address needs it to still be reachable, which
     * on mobile data it is not, and on a new Wi-Fi it may not be either.
     *
     * Registered against ANY network rather than Wi-Fi alone, because the event worth catching is
     * often the *loss* of Wi-Fi to a mobile connection that is perfectly healthy - a Wi-Fi-only
     * request would simply stop reporting and never fire.
     */
    private fun watchForNetworkChange() {
        networkWatch?.close()
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = checkSessionStillServable()
            override fun onLost(network: Network) = checkSessionStillServable()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                checkSessionStillServable()
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatchingNonFatal { connectivityManager.registerNetworkCallback(request, callback) }
            .onSuccess {
                networkWatch = AutoCloseable {
                    runCatchingNonFatal { connectivityManager.unregisterNetworkCallback(callback) }
                        .onFailure { e ->
                            AppLog.w(TAG) { "Cannot stop the DLNA network watch: ${e.javaClass.simpleName}" }
                        }
                }
            }
            .onFailure { e -> AppLog.w(TAG) { "Cannot watch the network for this session: ${e.javaClass.simpleName}" } }
    }

    private fun checkSessionStillServable() {
        if (_state.value.connectedDevice == null) return
        val current = LocalNetworkAddress.currentIpv4Address(appContext)
        if (DlnaNetworkChangePolicy.sessionSurvives(sessionHost, current)) return
        AppLog.d(TAG) { "The address this session was served from is gone; ending it" }
        // On the main thread deliberately: stop() touches _state and connectJob, and a
        // NetworkCallback arrives on a binder thread.
        scope.launch { stop() }
    }

    /** A last-resort net under [SsdpDiscovery]'s own per-stage catches - discovery failing must
     * leave the sheet with an empty list, never propagate. Cancellation is NOT a failure and is
     * rethrown (the sheet cancels this the moment it's dismissed), which is exactly what a
     * Kotlin's `runCatching` here would have swallowed once [SsdpDiscovery.discover] became suspending. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun discoverDevices(): List<DlnaDevice> = withContext(ioDispatcher) {
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
    fun connect(device: DlnaDevice, streamUrl: String, title: String) =
        startSession(device, streamUrl, title, isRepoint = false)

    private fun startSession(device: DlnaDevice, streamUrl: String, title: String, isRepoint: Boolean) {
        _state.update { it.copy(isConnecting = true) }
        val generation = connectGeneration.incrementAndGet()
        AppLog.d(TAG) {
            "DLNA attempt gen=$generation action=${if (isRepoint) "switch" else "connect"} started"
        }
        // This session ends by publishing the volume it read from the renderer itself, so an answer
        // still in flight for the previous one describes a state that is about to be replaced.
        val sessionVolumeGeneration = volumeGeneration.incrementAndGet()
        connectJob?.cancel()
        connectJob = scope.launch {
            val request = DlnaConnectRequest(device, streamUrl, title, isRepoint, generation)
            val connected = withContext(ioDispatcher) {
                connectAttempts.run(generation, connectGeneration::get) { checkpoint ->
                    connectBlocking(request, checkpoint)
                }
            }
            // Read after the session is up rather than at discovery: a renderer answers
            // RenderingControl whether or not it is playing, but reading it here means the number
            // shown is the one in force for this session, and one failed read costs the slider
            // rather than the cast.
            val reportedVolume = if (connected && device.renderingControlUrl != null) {
                withContext(ioDispatcher) {
                    volumeAttempts.runSerialized {
                        renderingControlClient.getVolume(device.renderingControlUrl)
                    }
                }
            } else {
                null
            }
            // A slider action can happen while a channel re-point is still reading the volume.
            // Its optimistic value is newer than this read and must not be overwritten when the
            // read returns; its serialized SetVolume will correct the renderer in the same order.
            val volume = if (sessionVolumeGeneration == volumeGeneration.get()) {
                reportedVolume
            } else {
                _state.value.volume
            }
            _state.value = when {
                connected -> DlnaConnectionState(connectedDevice = device, volume = volume)
                // A re-point that failed leaves the renderer where it was - still connected, still
                // playing the previous channel. Dropping to disconnected here would be a worse lie
                // than the stale channel name: the user would lose the Stop button for a cast that
                // is demonstrably still running, and the proxy feeding it is deliberately still up
                // (see connectBlocking).
                isRepoint -> DlnaConnectionState(connectedDevice = device, volume = _state.value.volume)
                else -> DlnaConnectionState()
            }
            // Started once there is something to watch over, and only then: a callback registered
            // for a connect that failed would outlive it with no session to end.
            if (_state.value.connectedDevice != null) watchForNetworkChange()
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
        startSession(device, streamUrl, title, isRepoint = true)
    }

    /**
     * Sets the renderer's volume, in two phases: the requested value is published immediately, then
     * replaced by what the renderer actually reports.
     *
     * Publishing straight away is what lets the slider be a plain function of this state with no
     * local copy of its own. Without it the thumb had to either snap back to the old volume for the
     * length of a LAN round trip, or hold a value of its own that a refused action would leave
     * standing as a lie.
     *
     * The read-back is what makes the optimism safe. A renderer whose real scale is smaller than
     * [VolumeRange.MAX] clamps or refuses a value above it, and the correction lands within the
     * round trip - visibly, on a control the user is still looking at. A refused action reverts to
     * the value that was in force before, because nothing changed on the TV. A set the renderer
     * accepted but whose read-back failed keeps the requested value: it is the best evidence there
     * is, and reverting would show a volume the TV demonstrably is not at.
     */
    fun setVolume(volume: Int) {
        val url = _state.value.connectedDevice?.renderingControlUrl ?: return
        val previous = _state.value.volume
        val requested = VolumeRange.clamp(volume)
        val generation = volumeGeneration.incrementAndGet()
        _state.update { it.copy(volume = requested) }
        scope.launch {
            val (accepted, reported) = withContext(ioDispatcher) {
                volumeAttempts.run(generation, volumeGeneration::get) { checkpoint ->
                    val wasAccepted = renderingControlClient.setVolume(url, requested)
                    checkpoint()
                    val readBack = if (wasAccepted) renderingControlClient.getVolume(url) else null
                    checkpoint()
                    wasAccepted to readBack
                }
            }
            // Two guards on one write. Still connected: a stop() that landed while this was in
            // flight must not resurrect a volume for a session that is over. Still current: dragging
            // the slider twice in quick succession leaves two round trips racing, and the older
            // one's answer describes a volume the user has already moved past.
            if (_state.value.connectedDevice != null && generation == volumeGeneration.get()) {
                _state.update { it.copy(volume = reported ?: if (accepted) requested else previous) }
            }
        }
    }

    private suspend fun connectBlocking(
        request: DlnaConnectRequest,
        checkpoint: suspend () -> Unit,
    ): Boolean {
        checkpoint()
        val prepared = prepareProxyForAttempt(request) ?: return false
        checkpoint()
        val correlationId = CorrelationId.from("dlna", prepared.token)
        AppLog.d(TAG) {
            "DLNA session=$correlationId attempt=${request.generation} " +
                "action=${if (request.isRepoint) "switch" else "connect"}"
        }

        // Always stop first, and ignore whether it worked. The AVTransport state table only
        // guarantees SetAVTransportURI from STOPPED and NO_MEDIA_PRESENT; from PLAYING it is
        // renderer-specific, and the ones that do accept it still spend time transitioning, which
        // is what made Play come back 701 four times in a row on a Samsung. Stopping first turns a
        // renderer-specific case into the one every renderer implements. The result is ignored on
        // purpose: a renderer that refuses Stop because it was not playing anyway has told us
        // nothing that should abort the connect.
        //
        // This was `if (isRepoint)` on the reasoning that a fresh connect finds an idle renderer.
        // A logcat says otherwise: a first connect to the Samsung took five refused Plays - three
        // seconds of the user watching nothing. Measuring the set directly afterwards (see
        // docs/DLNA.md) found the state it is actually left in: `stop()` below leaves this Samsung
        // in PAUSED_PLAYBACK, not STOPPED, and it stays there. So the state a first connect meets is
        // usually one *this app* left behind on its last session - and PAUSED_PLAYBACK is not one
        // of the two the AVTransport table guarantees SetAVTransportURI from.
        //
        // The same measurement is why the result is ignored rather than checked: the set answered
        // Stop with HTTP 200 and still reported PAUSED_PLAYBACK, so a success here would not have
        // meant the renderer was idle either.
        avTransportClient.stop(request.device.controlUrl)
        checkpoint()
        val uriAccepted = avTransportClient.setAvTransportUri(
            request.device.controlUrl,
            prepared.localUrl,
            request.title,
        )
        checkpoint()
        val ok = uriAccepted && avTransportClient.play(request.device.controlUrl)
        checkpoint()
        if (!ok) {
            AppLog.w(TAG) {
                "DLNA session=$correlationId attempt=${request.generation} " +
                    "${if (request.isRepoint) "channel switch" else "connect"} " +
                    "failed for ${request.device.friendlyName}"
            }
            // Only a first connect tears the session down. On a re-point the renderer is still
            // playing the previous channel *through this proxy* - stopping it was what put an error
            // on the TV, since the failure is usually a refused action rather than a dead stream.
            //
            // And only the attempt that is still current may tear anything down (see
            // [connectGeneration]): a superseded attempt's failure says nothing about the session
            // that replaced it, and this teardown would take that session's proxy with it.
            if (!request.isRepoint) tearDownProxyIfCurrent(request.generation)
        } else {
            AppLog.d(TAG) { "DLNA session=$correlationId attempt=${request.generation} connected" }
        }
        return ok
    }

    private fun prepareProxyForAttempt(request: DlnaConnectRequest): PreparedDlnaProxy? {
        val host = LocalNetworkAddress.currentIpv4Address(appContext)
        if (host == null) {
            AppLog.w(TAG) { "No LAN address available; cannot start DLNA proxy" }
            return null
        }
        return runCatchingNonFatal {
            prepareProxy(request, host)
        }.getOrElse { error ->
            AppLog.w(TAG) { "DLNA proxy setup failed: ${error.javaClass.simpleName}" }
            tearDownProxyIfCurrent(request.generation)
            null
        }
    }

    /**
     * Atomically claims the proxy for [generation]. [stop] increments the generation before taking
     * the same lock, so either this setup wins and stop tears it down afterwards, or stop wins and
     * this stale attempt is rejected before it can restart the foreground service.
     */
    private fun prepareProxy(request: DlnaConnectRequest, host: String): PreparedDlnaProxy =
        connectAttempts.withCurrentLifecycle(request.generation, connectGeneration::get) {
            // The foreground service owns the CPU/Wi-Fi locks for exactly the proxy's lifetime.
            CastProxyService.start(
                appContext,
                request.title,
                request.device.friendlyName,
                CastProxyTarget.DLNA,
            )
            val token = sessionToken ?: UUID.randomUUID().toString().also { sessionToken = it }
            // DLNA renderers prefer continuous MPEG-TS. Raw TS therefore stays raw, while an HLS
            // origin is flattened when possible and safely falls back to its manifest otherwise.
            proxyServer.ensureStarted(
                sessionToken = token,
                host = host,
                remuxEnabled = false,
                flattenHlsToStream = true,
            )
            val localUrl = proxyServer.buildLocalUrl(proxyServer.registerPlaylist(request.streamUrl))
            sessionHost = host
            PreparedDlnaProxy(token, localUrl)
        }

    private fun tearDownProxyIfCurrent(generation: Long) = connectAttempts.withLifecycle {
        if (generation == connectGeneration.get()) tearDownProxyLocked()
    }

    /** Caller owns [LatestDlnaAttemptSerializer.withLifecycle]. */
    private fun tearDownProxyLocked(): String? {
        val token = sessionToken
        sessionToken = null
        sessionHost = null
        proxyServer.stop()
        CastProxyService.stop(appContext, CastProxyTarget.DLNA)
        return token
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
        // Retires any in-flight attempt along with the session: without this, a connect still
        // blocked on a renderer that never answers would come back after the user had already
        // stopped, find its own generation current, and tear down a second time.
        connectGeneration.incrementAndGet()
        // And the same for volume: see volumeGeneration. The next session reads its own.
        volumeGeneration.incrementAndGet()
        connectJob?.cancel()
        connectJob = null
        networkWatch?.close()
        networkWatch = null
        val stoppedToken = connectAttempts.withLifecycle { tearDownProxyLocked() }
        stoppedToken?.let { token ->
            AppLog.d(TAG) { "DLNA session=${CorrelationId.from("dlna", token)} stopped" }
        }
        if (device != null) {
            // Acquire the same FIFO command lane before this method returns. A new connect then
            // cannot overtake the old renderer's deferred SOAP Stop and be stopped by it later.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                connectAttempts.runSerialized {
                    withContext(ioDispatcher) { avTransportClient.stop(device.controlUrl) }
                }
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
