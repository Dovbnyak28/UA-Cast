package com.uacastplayer.cast

import android.content.Context
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.uacastplayer.data.cast.CastWakeLocks
import com.uacastplayer.data.cast.IncompatibilityMemoryStore
import com.uacastplayer.data.cast.LocalNetworkAddress
import com.uacastplayer.data.cast.ProxyServer
import com.uacastplayer.data.cast.TsFirstSegmentDiagnostic
import com.uacastplayer.log.AppLog
import java.util.UUID
import java.util.concurrent.TimeUnit
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
import okhttp3.OkHttpClient

private const val TAG = "CastSessionRepository"
private const val WATCHDOG_TIMEOUT_MILLIS = 4_000L

private data class ActiveChannel(val index: Int, val streamUrl: String, val title: String)

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
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val proxyServer = ProxyServer(httpClient)
    private val wakeLocks = CastWakeLocks(appContext)
    private val incompatibilityStore = IncompatibilityMemoryStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var castContext: CastContext? = null
    private var currentSession: CastSession? = null
    private var currentReceiverId: String? = null
    private var activeChannel: ActiveChannel? = null
    private var watchdogJob: Job? = null

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
            applyResult(CastReceiverStatusReducer.reduce(_state.value, receiverStatus, idleReason))
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
    fun setActiveChannel(index: Int, streamUrl: String, title: String) {
        activeChannel = ActiveChannel(index, streamUrl, title)
        if (currentSession != null) {
            _state.value = CastReceiverStatusReducer.requestChannelSwitch(_state.value, index)
            startPlayback(streamUrl, title)
        }
    }

    private fun onSessionActive(session: CastSession) {
        currentSession = session
        currentReceiverId = session.castDevice?.deviceId
        session.remoteMediaClient?.registerCallback(remoteMediaClientCallback)
        activeChannel?.let { startPlayback(it.streamUrl, it.title) }
    }

    private fun onSessionInactive() {
        watchdogJob?.cancel()
        currentSession?.remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
        currentSession = null
        currentReceiverId = null
        applyResult(CastReceiverStatusReducer.reduce(_state.value, ReceiverStatus.DISCONNECTED))
    }

    private fun startPlayback(streamUrl: String, title: String) {
        watchdogJob?.cancel()
        val receiverId = currentReceiverId.orEmpty()
        val record = incompatibilityStore.lookup(streamUrl, receiverId)
        val knownIncompatible = IncompatibilityMemoryPolicy.shouldGoStraightToProxy(record, System.currentTimeMillis())
        val mode = CastDeliveryStrategy.initialMode(knownIncompatible)
        _state.update { it.copy(deliveryMode = mode) }

        when (mode) {
            CastDeliveryMode.Direct -> loadDirectWithWatchdog(streamUrl, title)
            CastDeliveryMode.Proxy -> startProxyAndLoad(streamUrl, title)
        }
    }

    private fun loadDirectWithWatchdog(streamUrl: String, title: String) {
        loadOnReceiver(streamUrl, title, originalStreamUrl = streamUrl)

        watchdogJob = scope.launch {
            val diagnostic = async(Dispatchers.IO) {
                runCatching { TsFirstSegmentDiagnostic.isKnownUnsupported(streamUrl, httpClient) }.getOrDefault(false)
            }
            launch {
                if (diagnostic.await()) fallBackToProxyIfStillDirect(streamUrl, title, "unsupported_codec_detected")
            }
            delay(WATCHDOG_TIMEOUT_MILLIS)
            if (_state.value.receiverStatus != ReceiverStatus.PLAYING) {
                fallBackToProxyIfStillDirect(streamUrl, title, "watchdog_timeout")
            }
        }
    }

    private fun fallBackToProxyIfStillDirect(streamUrl: String, title: String, reason: String) {
        if (_state.value.deliveryMode != CastDeliveryMode.Direct) return
        AppLog.d(TAG) { "Falling back to proxy: $reason" }
        _state.update { it.copy(deliveryMode = CastDeliveryMode.Proxy) }
        startProxyAndLoad(streamUrl, title)
    }

    private fun startProxyAndLoad(streamUrl: String, title: String) {
        val host = LocalNetworkAddress.currentIpv4Address()
        if (host == null) {
            AppLog.w(TAG) { "No LAN address available; cannot start proxy fallback" }
            giveUp(streamUrl)
            return
        }
        proxyServer.start(sessionToken = UUID.randomUUID().toString(), host = host)
        wakeLocks.acquire()
        val resourceId = proxyServer.registerPlaylist(streamUrl)
        loadOnReceiver(proxyServer.buildLocalUrl(resourceId), title, originalStreamUrl = streamUrl)
    }

    private fun loadOnReceiver(urlToLoad: String, title: String, originalStreamUrl: String) {
        val client = currentSession?.remoteMediaClient ?: return
        _state.update { it.copy(loadPhase = CastLoadPhase.LOADING) }
        val request = CastMediaLoader.buildRequest(urlToLoad, title)
        client.load(request).setResultCallback { result ->
            val loadResult = if (result.status.isSuccess) {
                CastLoadResult.Success
            } else {
                CastLoadResult.Failure("status_${result.status.statusCode}")
            }
            handleLoadResult(loadResult, originalStreamUrl, title)
        }
    }

    private fun handleLoadResult(result: CastLoadResult, streamUrl: String, title: String) {
        if (result is CastLoadResult.Failure && _state.value.deliveryMode == CastDeliveryMode.Direct) {
            watchdogJob?.cancel()
            _state.update { it.copy(deliveryMode = CastDeliveryMode.Proxy) }
            startProxyAndLoad(streamUrl, title)
            return
        }
        if (result is CastLoadResult.Failure) {
            currentReceiverId?.let { incompatibilityStore.record(streamUrl, it) }
        }
        applyResult(CastLoadResultReducer.reduce(_state.value, result))
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
                wakeLocks.release()
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
