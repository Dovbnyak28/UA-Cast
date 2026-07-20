package com.uacastplayer.cast

import android.content.Context
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.data.cast.IncompatibilityMemoryStore
import com.uacastplayer.data.cast.LocalNetworkAddress
import com.uacastplayer.data.cast.ProxyServer
import com.uacastplayer.data.cast.TsFirstSegmentDiagnostic
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.log.AppLog
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "CastSessionRepository"
private const val WATCHDOG_TIMEOUT_MILLIS = 4_000L

private data class ActiveChannel(
    val index: Int,
    val streamUrl: String,
    val title: String,
    val userAgent: String?,
    val referrer: String?,
)

/** What [CastSessionRepository.applyLoadResult] needs to retry on the proxy if a direct load fails. */
private data class LoadRetryContext(
    val streamUrl: String,
    val title: String,
    val userAgent: String?,
    val referrer: String?,
)

/**
 * App-wide singleton (not a ViewModel, since a Cast session must survive navigating away from and
 * back to the player) wrapping the real GMS Cast callbacks. Owns the full direct-then-proxy
 * delivery pipeline: direct-first playback per [CastDeliveryStrategy], a watchdog that falls back
 * to the local [ProxyServer] if the receiver isn't PLAYING within 4s (or immediately if
 * [TsFirstSegmentDiagnostic] already flagged the codec as unsupported), and [IncompatibilityMemoryStore]
 * so a (stream, receiver) pair that failed once goes straight to proxy for the next 30 days.
 * [CastLoadResultReducer] / [CastReceiverStatusReducer] remain the pure source of truth for state
 * transitions; this class is the impure glue driving them from real callbacks and timers.
 */
class CastSessionRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val httpClient = AppHttp.client(connectTimeoutSeconds = 10, readTimeoutSeconds = 15)
    private val proxyServer = ProxyServer(httpClient)
    private val preferences = AppPreferences(appContext)
    private val incompatibilityStore = IncompatibilityMemoryStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var castContext: CastContext? = null
    private var currentSession: CastSession? = null
    private var currentReceiverId: String? = null
    private var activeChannel: ActiveChannel? = null
    private var watchdogJob: Job? = null

    // See loadOnReceiver/handleLoadResult: every load() bumps this before the SDK call, so a
    // result callback for a load a newer one has already superseded (a watchdog fallback or a fast
    // channel switch) can be told apart from the result of the current, still-relevant request.
    private val loadGeneration = AtomicLong(0)

    // True only until the very next receiver status update - see CastReceiverStatusReducer.reduce.
    private var selfInitiatedTransition = false

    // Not a real cache (see cast/CastRecoveryPolicy and the diagnostic's own LruCache for that) -
    // just the most recently resolved sourceKind for the in-flight channel, so a load issued after
    // the diagnostic already answered (proxy fallback, a future reload) gets the right Cast
    // content-type instead of falling back to a URL guess. Reset per channel in startPlayback.
    private var lastKnownSourceKind: TsSourceKind? = null

    // One token for the whole cast session, not per load - see ProxyServer.ensureStarted. Every
    // channel switch during that session reuses it so the proxy's port/resources/remux session
    // survive the switch instead of being torn down and rebuilt from under the receiver.
    private var proxySessionToken: String = ""

    private val _state = MutableStateFlow(CastPlaybackState())
    val state: StateFlow<CastPlaybackState> = _state.asStateFlow()

    private val _sideEffects = MutableSharedFlow<CastSideEffect>(extraBufferCapacity = 8)
    val sideEffects: SharedFlow<CastSideEffect> = _sideEffects.asSharedFlow()

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStarted(session: CastSession, sessionId: String) = onSessionActive(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) = onSessionInactive()
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onSessionActive(session)
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = onSessionInactive()
    }

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val status = currentSession?.remoteMediaClient?.mediaStatus ?: return
            val receiverStatus = mapPlayerState(status.playerState)
            if (receiverStatus == ReceiverStatus.PLAYING) watchdogJob?.cancel()
            val idleReason = mapIdleReason(status.idleReason)
            val selfInitiated = selfInitiatedTransition
            selfInitiatedTransition = false
            applyResult(CastReceiverStatusReducer.reduce(_state.value, receiverStatus, idleReason, selfInitiated))
        }
    }

    init {
        try {
            castContext = CastContext.getSharedInstance(appContext)
            castContext?.sessionManager?.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
        } catch (e: Exception) {
            AppLog.w(TAG) { "Cast context unavailable: ${e.javaClass.simpleName}" }
        }
    }

    /**
     * Called by the player whenever its active channel changes, cast or not. While a session is
     * connected this both queues the index as pending (for handoff on disconnect) and starts
     * delivering the new channel to the receiver immediately.
     */
    fun setActiveChannel(index: Int, streamUrl: String, title: String, userAgent: String? = null, referrer: String? = null) {
        activeChannel = ActiveChannel(index, streamUrl, title, userAgent, referrer)
        if (currentSession != null) {
            _state.value = CastReceiverStatusReducer.requestChannelSwitch(_state.value, index)
            startPlayback(streamUrl, title, userAgent, referrer)
        }
    }

    private fun onSessionActive(session: CastSession) {
        currentSession = session
        currentReceiverId = session.castDevice?.deviceId
        proxySessionToken = UUID.randomUUID().toString()
        session.remoteMediaClient?.registerCallback(remoteMediaClientCallback)
        startProxyEagerly()
        // Covers starting a session from the player while a channel is already open, not just
        // switching channels mid-session: setActiveChannel() records every channel the player
        // opens (including the very first one, via start()), so activeChannel is already set by
        // the time a session connects here even if no switch happened while casting was active.
        activeChannel?.let { startPlayback(it.streamUrl, it.title, it.userAgent, it.referrer) }
    }

    /**
     * Starts the proxy server (a `ServerSocket` bind + thread pool - cheap, see
     * docs/PROXY_RULES.md) the moment a cast session connects, rather than waiting for an actual
     * fallback to need it - if one does turn out to be needed (codec incompatibility, watchdog
     * timeout), [startProxyAndLoad] doesn't also pay for the server startup at that point, only for
     * registering the one resource it actually needs. Safe to call unconditionally even for a
     * session that never falls back: [ProxyServer.ensureStarted] is a no-op if already running for
     * this session's token, and every session end ([CastReceiverStatusReducer.reduceDisconnected])
     * unconditionally emits [CastSideEffect.CloseProxySession], so an eagerly-started-but-never-used
     * proxy still gets torn down like any other.
     */
    private fun startProxyEagerly() {
        val host = LocalNetworkAddress.currentIpv4Address(appContext) ?: return
        proxyServer.ensureStarted(
            sessionToken = proxySessionToken,
            host = host,
            remuxEnabled = preferences.rawTsRemuxEnabled,
        )
    }

    private fun onSessionInactive() {
        watchdogJob?.cancel()
        currentSession?.remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
        currentSession = null
        currentReceiverId = null
        applyResult(CastReceiverStatusReducer.reduce(_state.value, ReceiverStatus.DISCONNECTED))
    }

    private fun startPlayback(streamUrl: String, title: String, userAgent: String?, referrer: String?) {
        watchdogJob?.cancel()
        val receiverId = currentReceiverId.orEmpty()
        val record = incompatibilityStore.lookup(streamUrl, receiverId)
        val knownIncompatible = IncompatibilityMemoryPolicy.shouldGoStraightToProxy(record, System.currentTimeMillis())
        val mode = CastDeliveryStrategy.initialMode(knownIncompatible)
        lastKnownSourceKind = null
        _state.update {
            it.copy(
                deliveryMode = mode,
                codecIncompatibility = null,
                receiverLoadFailed = false,
                likelyCompatibilityHint = null,
            )
        }

        when (mode) {
            CastDeliveryMode.Direct -> loadDirectWithWatchdog(streamUrl, title, userAgent, referrer)
            CastDeliveryMode.Proxy -> startProxyAndLoad(streamUrl, title, userAgent, referrer)
        }
    }

    private fun loadDirectWithWatchdog(streamUrl: String, title: String, userAgent: String?, referrer: String?) {
        loadOnReceiver(streamUrl, title, originalStreamUrl = streamUrl, userAgent = userAgent, referrer = referrer)

        watchdogJob = scope.launch {
            val diagnostic = async(Dispatchers.IO) {
                TsFirstSegmentDiagnostic.diagnose(streamUrl, httpClient)
            }
            launch {
                val result = diagnostic.await()
                // The diagnostic runs on the IO dispatcher, genuinely concurrently with the user
                // zapping to another channel on the Main dispatcher - see StaleChannelGuard.
                if (!StaleChannelGuard.isCurrent(streamUrl, activeChannel?.streamUrl)) {
                    AppLog.d(TAG) { "cast route: stale diagnostic result ignored, channel no longer active" }
                    return@launch
                }
                lastKnownSourceKind = result.sourceKind
                val verdict = CastCompatibilityPolicy.classify(result.programInfo)
                val decision = CastDeliveryStrategy.onDiagnosticResult(verdict, result.sourceKind)
                // One self-contained line per routing decision - no URL, just what was found and
                // what it led to, so a field logcat is enough to diagnose a cast failure on its own.
                AppLog.d(TAG) {
                    "cast route: verdict=$verdict source=${result.sourceKind} action=$decision " +
                        "video=${result.programInfo?.videoCodec} audio=${result.programInfo?.audioCodecs}"
                }
                // Never blocks or reroutes anything by itself - just remembered in case
                // receiverLoadFailed ends up true later, so that message can name a likely cause.
                if (verdict is CastCompatibilityVerdict.LikelyCompatible) {
                    _state.update { it.copy(likelyCompatibilityHint = verdict) }
                }
                when (decision) {
                    is CastRouteDecision.Blocked -> onRouteBlocked(streamUrl, decision.verdict)
                    CastRouteDecision.ProxyImmediately ->
                        fallBackToProxyIfStillDirect(streamUrl, title, userAgent, referrer, "raw_ts_compatible")
                    CastRouteDecision.NoAction -> Unit
                }
            }
            delay(WATCHDOG_TIMEOUT_MILLIS)
            if (_state.value.receiverStatus != ReceiverStatus.PLAYING && _state.value.codecIncompatibility == null) {
                fallBackToProxyIfStillDirect(streamUrl, title, userAgent, referrer, "watchdog_timeout")
            }
        }
    }

    /** A confirmed-incompatible codec verdict: remuxing the container never fixes a codec problem
     * (see [com.uacastplayer.proxy.RawTsRemuxActivation]'s own doc), so - unlike the generic
     * watchdog-timeout fallback - this never proceeds to the proxy at all, and the (stream,
     * receiver) pair is recorded as incompatible immediately rather than waiting for an actual
     * receiver-side failure to do it. */
    private fun onRouteBlocked(streamUrl: String, verdict: CastCompatibilityVerdict.IncompatibleVideo) {
        if (!StaleChannelGuard.isCurrent(streamUrl, activeChannel?.streamUrl)) return
        currentReceiverId?.let { incompatibilityStore.record(streamUrl, it) }
        reportCodecIncompatibility(CodecIncompatibility.Video(verdict.codec))
    }

    private fun reportCodecIncompatibility(incompatibility: CodecIncompatibility) {
        AppLog.d(TAG) { "Cast codec incompatibility detected: $incompatibility" }
        _state.update { it.copy(codecIncompatibility = incompatibility) }
    }

    private fun fallBackToProxyIfStillDirect(streamUrl: String, title: String, userAgent: String?, referrer: String?, reason: String) {
        if (_state.value.deliveryMode != CastDeliveryMode.Direct) return
        if (!StaleChannelGuard.isCurrent(streamUrl, activeChannel?.streamUrl)) return
        AppLog.d(TAG) { "Falling back to proxy: $reason" }
        _state.update { it.copy(deliveryMode = CastDeliveryMode.Proxy) }
        startProxyAndLoad(streamUrl, title, userAgent, referrer)
    }

    private fun startProxyAndLoad(streamUrl: String, title: String, userAgent: String?, referrer: String?) {
        val host = LocalNetworkAddress.currentIpv4Address(appContext)
        if (host == null) {
            AppLog.w(TAG) { "No LAN address available; cannot start proxy fallback" }
            giveUp(streamUrl)
            return
        }
        proxyServer.ensureStarted(
            sessionToken = proxySessionToken,
            host = host,
            remuxEnabled = preferences.rawTsRemuxEnabled,
        )
        applyProxyLifecycle(ProxyLifecycleEvent.STARTED, channelTitle = title, receiverName = currentSession?.castDevice?.friendlyName)
        val resourceId = proxyServer.registerPlaylist(streamUrl, userAgent, referrer)
        val localUrl = proxyServer.buildLocalUrl(resourceId)
        AppLog.d(TAG) { "Proxy fallback loading receiver from $localUrl" }
        loadOnReceiver(localUrl, title, originalStreamUrl = streamUrl, userAgent = userAgent, referrer = referrer)
    }

    private fun loadOnReceiver(
        urlToLoad: String,
        title: String,
        originalStreamUrl: String,
        userAgent: String? = null,
        referrer: String? = null,
    ) {
        val client = currentSession?.remoteMediaClient ?: return
        val generation = loadGeneration.incrementAndGet()
        selfInitiatedTransition = true
        _state.update { it.copy(loadPhase = CastLoadPhase.LOADING) }
        val request = CastMediaLoader.buildRequest(urlToLoad, title, lastKnownSourceKind)
        val context = LoadRetryContext(originalStreamUrl, title, userAgent, referrer)
        client.load(request).setResultCallback { result ->
            val loadResult = if (result.status.isSuccess) {
                CastLoadResult.Success
            } else {
                CastLoadResult.Failure("status_${result.status.statusCode}")
            }
            handleLoadResult(generation, result.status.statusCode, loadResult, context)
        }
    }

    /** First ignores anything from a load a newer request has already superseded - see
     * [loadGeneration] - then, for a same-generation failure, tells apart a status the Cast SDK
     * only reports *because* this request got superseded (see [LoadStatusOutcome.Superseded]) from
     * a genuine failure of this specific request, which still goes through the normal fail path. */
    private fun handleLoadResult(generation: Long, statusCode: Int, result: CastLoadResult, context: LoadRetryContext) {
        if (generation != loadGeneration.get()) {
            AppLog.d(TAG) { "cast load: gen=$generation status=stale action=ignored" }
            return
        }
        logLoadOutcome(generation, statusCode, result, context)
    }

    private fun logLoadOutcome(generation: Long, statusCode: Int, result: CastLoadResult, context: LoadRetryContext) {
        val outcome = (result as? CastLoadResult.Failure)?.let { LoadStatusClassifier.classify(statusCode) }
        val superseded = outcome is LoadStatusOutcome.Superseded
        val statusLabel = outcome?.let { "${it.statusName}($statusCode)" } ?: "SUCCESS"
        val action = if (outcome == null) "loaded" else if (superseded) "ignored" else "handled"
        AppLog.d(TAG) { "cast load: gen=$generation status=$statusLabel action=$action" }
        if (!superseded) applyLoadResult(result, context)
    }

    private fun applyLoadResult(result: CastLoadResult, context: LoadRetryContext) {
        // Lets a still-draining previous channel's remux session (see ProxyServer/RemuxHandoffPolicy)
        // be torn down right away instead of waiting out its grace period - a harmless no-op if this
        // load wasn't on the proxy or nothing was draining.
        if (result is CastLoadResult.Success) proxyServer.confirmActiveSession()
        // A confirmed-incompatible codec (see onRouteBlocked) means the proxy could never have
        // helped even if the diagnostic resolved after this failure - don't waste a proxy attempt
        // chasing a load that was already known to be futile.
        val isDirectFailure = result is CastLoadResult.Failure && _state.value.deliveryMode == CastDeliveryMode.Direct
        val stillWorthProxying = _state.value.codecIncompatibility == null
        if (isDirectFailure && stillWorthProxying) {
            watchdogJob?.cancel()
            _state.update { it.copy(deliveryMode = CastDeliveryMode.Proxy) }
            startProxyAndLoad(context.streamUrl, context.title, context.userAgent, context.referrer)
            return
        }
        applyResult(CastLoadResultReducer.reduce(_state.value, result))
    }

    /** Ends the active Cast session outright - used by [CastProxyService]'s notification "Stop" action. */
    fun endSession() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    private fun applyProxyLifecycle(event: ProxyLifecycleEvent, channelTitle: String? = null, receiverName: String? = null) {
        when (ProxySessionPolicy.commandFor(event)) {
            ProxyServiceCommand.StartForeground ->
                CastProxyService.start(appContext, channelTitle.orEmpty(), receiverName.orEmpty())
            ProxyServiceCommand.StopForeground -> CastProxyService.stop(appContext)
        }
    }

    private fun giveUp(streamUrl: String) {
        currentReceiverId?.let { incompatibilityStore.record(streamUrl, it) }
        applyResult(CastLoadResultReducer.reduce(_state.value, CastLoadResult.Failure("proxy_unavailable")))
    }

    private fun applyResult(result: CastReducerResult) {
        _state.value = result.state
        for (effect in result.effects) {
            if (effect is CastSideEffect.CloseProxySession) {
                proxyServer.stop()
                applyProxyLifecycle(ProxyLifecycleEvent.STOPPED)
            }
            if (!_sideEffects.tryEmit(effect)) {
                AppLog.w(TAG) { "Dropped cast side effect, no buffer space: ${effect.javaClass.simpleName}" }
            }
        }
    }

    private fun mapPlayerState(playerState: Int): ReceiverStatus = when (playerState) {
        MediaStatus.PLAYER_STATE_PLAYING -> ReceiverStatus.PLAYING
        MediaStatus.PLAYER_STATE_PAUSED -> ReceiverStatus.PAUSED
        MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> ReceiverStatus.BUFFERING
        else -> ReceiverStatus.IDLE
    }

    private fun mapIdleReason(reason: Int): IdleReason = when (reason) {
        MediaStatus.IDLE_REASON_FINISHED -> IdleReason.FINISHED
        MediaStatus.IDLE_REASON_ERROR -> IdleReason.ERROR
        MediaStatus.IDLE_REASON_CANCELED -> IdleReason.CANCELLED
        MediaStatus.IDLE_REASON_INTERRUPTED -> IdleReason.INTERRUPTED
        else -> IdleReason.NONE
    }

    companion object {
        @Volatile private var instance: CastSessionRepository? = null

        fun getInstance(context: Context): CastSessionRepository =
            instance ?: synchronized(this) {
                instance ?: CastSessionRepository(context).also { instance = it }
            }
    }
}
