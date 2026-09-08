package com.uacastplayer.cast

import com.uacastplayer.core.cast.CastRouteKind
import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.IncompatibilityMemoryPolicy
import com.uacastplayer.core.cast.TsSourceKind
import android.content.Context
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastSession
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.data.cast.IncompatibilityMemoryStore
import com.uacastplayer.data.cast.LocalNetworkAddress
import com.uacastplayer.data.cast.TsFirstSegmentDiagnostic
import com.uacastplayer.diagnostics.CastRouteOutcome
import com.uacastplayer.diagnostics.CorrelationId
import com.uacastplayer.diagnostics.RemuxEffectivenessStore
import com.uacastplayer.log.AppLog
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "CastSessionRepository"
// The direct-mode watchdog only, now that the stall watchdog ticks on delivered bytes instead (see
// CastStallWatchdogPolicy). The two decide different questions and a flat 4s is right for this one:
// in direct mode nothing travels through the phone, so the receiver fetching the origin itself has
// no reason to be slow, and the consequence of firing is a cheap mode switch to the proxy - not the
// destructive reload that made the same number wrong for the stall watchdog.
private const val WATCHDOG_TIMEOUT_MILLIS = 4_000L

/**
 * Everything [CastSessionRepository] needs about the channel being cast. A value type rather than a
 * parameter list because it is exactly the same set of fields in both directions - the caller
 * describing a channel, and this class remembering the active one - and that list had grown long
 * enough that the two were only kept in step by hand.
 */
data class CastChannel(
    val index: Int,
    val streamUrl: String,
    val title: String,
    val userAgent: String? = null,
    val referrer: String? = null,
    /** The channel's artwork URL, shown on the receiver - see [CastMediaLoader]. Resolved by the
     * caller out of the full icon candidate chain, not just `tvg-logo`, so the TV shows the same
     * logo the phone does - see [com.uacastplayer.icons.CastArtworkPolicy]. Not part of
     * [LoadRetryContext] because nothing about *retrying* depends on it: a wrong thumbnail on a
     * racing channel switch is cosmetic, where a wrong stream URL would not be. */
    val logoUrl: String? = null,
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
 * to the local [CastProxySession] if the receiver isn't PLAYING within 4s (or immediately if
 * [TsFirstSegmentDiagnostic] already flagged the codec as unsupported), and [IncompatibilityMemoryStore]
 * so a (stream, receiver) pair that failed once goes straight to proxy for the next 30 days.
 * [CastLoadResultReducer] / [CastReceiverStatusReducer] remain the pure source of truth for state
 * transitions; this class is the impure glue driving them from real callbacks and timers.
 */
class CastSessionRepository private constructor(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
) {

    private val appContext = context.applicationContext
    private val httpClient = AppHttp.client(connectTimeoutSeconds = 10, readTimeoutSeconds = 15)
    private val remuxEffectivenessStore = RemuxEffectivenessStore.getInstance(appContext)
    private val proxy = CastProxySession(appContext, httpClient)
    private val incompatibilityStore = IncompatibilityMemoryStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var currentSession: CastSession? = null
    private var currentReceiverId: String? = null
    private var activeChannel: CastChannel? = null
    private val diagnosticCoordinator = CastDiagnosticCoordinator(
        scope = scope,
        activeStreamUrl = { activeChannel?.streamUrl },
        diagnose = { streamUrl -> TsFirstSegmentDiagnostic.diagnose(streamUrl, httpClient) },
    )
    private var watchdogJob: Job? = null
    private var suspensionJob: Job? = null
    private var suspendedChannel: CastChannel? = null

    // See loadOnReceiver/handleLoadResult: every load() bumps this before the SDK call, so a
    // result callback for a load a newer one has already superseded (a watchdog fallback or a fast
    // channel switch) can be told apart from the result of the current, still-relevant request.
    private val loadGeneration = CastLoadGeneration()

    // True only until the very next receiver status update - see CastReceiverStatusReducer.reduce.
    private var selfInitiatedTransition = false

    // Not a real cache (see cast/CastRecoveryPolicy and the diagnostic's own LruCache for that) -
    // just the most recently resolved sourceKind for the in-flight channel, so a load issued after
    // the diagnostic already answered (proxy fallback, a future reload) gets the right Cast
    // content-type instead of falling back to a URL guess. Reset per channel in startPlayback.
    private var lastKnownSourceKind: TsSourceKind? = null

    private val recovery = CastRecoveryRuntime(
        scope = scope,
        activeChannel = { activeChannel },
        reload = ::performRecoveryReload,
    )

    private val routeHistory = CastRouteHistory(
        observe = {
            CastRouteObservation(
                activeChannel?.streamUrl, currentReceiverId, _state.value.deliveryMode, currentRouteKind(),
            )
        },
        remember = incompatibilityStore::record,
        record = remuxEffectivenessStore::record,
    )
    private var sessionCorrelationId: String? = null
    private val channelSwitchSequence = AtomicLong(0)
    /** Content id of the receiver load whose status callbacks are currently relevant. */
    private var expectedReceiverContentId: String? = null

    private val _state = MutableStateFlow(CastPlaybackState())
    val state: StateFlow<CastPlaybackState> = _state.asStateFlow()

    // Receiver status can repeat PLAYING/BUFFERING while the player is busy rendering a frame.
    // Keep the stream bounded, prefer the newest recovery command, and coalesce duplicate local
    // pause commands below so a slow UI collector cannot starve a later Resume/Apply command.
    private val _sideEffects = MutableSharedFlow<CastSideEffect>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val sideEffects: SharedFlow<CastSideEffect> = _sideEffects.asSharedFlow()
    private var lastLocalControlEffect: CastSideEffect? = null

    private val playbackWatchdogs = CastPlaybackWatchdogs(
        scope = scope,
        inputs = CastWatchdogInputs(
            currentGeneration = { loadGeneration.current },
            activeStreamUrl = { activeChannel?.streamUrl },
            receiverStatus = { _state.value.receiverStatus },
            deliveryMode = { _state.value.deliveryMode },
            everReachedPlaying = { routeHistory.everReachedPlaying },
            bytesServedToReceiver = proxy::bytesServedToReceiver,
        ),
        onFailure = ::handleWatchdogFailure,
    )

    private val sessionLifecycle = CastSessionLifecycle(
        context = appContext,
        ioDispatcher = ioDispatcher,
        hasCurrentSession = { currentSession != null },
        onEvent = ::handleSessionEvent,
    )
    /** Re-created for every CastSession so a queued GMS callback carries its owner identity. */
    private var remoteMediaClientCallback: CastSdkRemoteMediaCallback? = null

    private fun handleSessionEvent(event: CastSdkSessionEvent) {
        when (event) {
            is CastSdkSessionEvent.Started -> onSessionActive(event.session, event.sessionId)
            is CastSdkSessionEvent.StartFailed -> AppLog.w(TAG) {
                "cast session start failed: error=${event.error}"
            }
            is CastSdkSessionEvent.Ended -> {
                if (!CastSessionIdentityGuard.isCurrent(event.session, currentSession)) {
                    AppLog.d(TAG) { "cast session: stale ended callback ignored" }
                    return
                }
                AppLog.d(TAG) { "cast session=${sessionCorrelationId ?: "unknown"} ended error=${event.error}" }
                onSessionInactive()
            }
            is CastSdkSessionEvent.Resuming -> {
                sessionCorrelationId = CorrelationId.from("cast", event.sessionId)
            }
            is CastSdkSessionEvent.Resumed -> {
                if (sessionLifecycle.isCurrent(event.session)) onSessionActive(event.session)
            }
            is CastSdkSessionEvent.ResumeFailed -> {
                if (event.session === currentSession) onSessionInactive()
            }
            is CastSdkSessionEvent.Suspended -> {
                if (!CastSessionIdentityGuard.isCurrent(event.session, currentSession)) {
                    AppLog.d(TAG) { "cast session: stale suspended callback ignored" }
                    return
                }
                AppLog.d(TAG) { "cast session=${sessionCorrelationId ?: "unknown"} suspended reason=${event.reason}" }
                onSessionSuspended()
            }
        }
    }

    private fun handleRemoteMediaStatus(callbackSession: CastSession) {
        if (_state.value.isSessionSuspended) return
        val status = callbackSession.remoteMediaClient?.mediaStatus ?: return
        when {
            !CastSessionIdentityGuard.isCurrent(callbackSession, currentSession) -> {
                AppLog.d(TAG) { "cast media status: stale session callback ignored" }
            }
            !CastStatusContentPolicy.shouldAccept(
                statusContentId = status.mediaInfo?.contentId,
                expectedContentId = expectedReceiverContentId,
            ) -> {
                // The callback still belongs to the current CastSession, but its media item is
                // from a channel load already superseded by a newer one. Processing it could
                // cancel the new channel's watchdog or mark the new route as PLAYING/FAILED with
                // stale evidence.
                AppLog.d(TAG) { "cast media status: stale media item ignored" }
            }
            else -> handleAcceptedRemoteMediaStatus(status)
        }
    }

    private fun handleAcceptedRemoteMediaStatus(status: MediaStatus) {
        val receiverStatus = mapPlayerState(status.playerState)
        if (receiverStatus == ReceiverStatus.PLAYING) {
            watchdogJob?.cancel()
        }
        playbackWatchdogs.onReceiverStatus(receiverStatus)
        val idleReason = mapIdleReason(status.idleReason)
        val selfInitiated = selfInitiatedTransition
        selfInitiatedTransition = false
        handleReceiverStatus(receiverStatus, idleReason, selfInitiated)
    }

    private fun handleWatchdogFailure(failure: CastWatchdogFailure) {
        when (failure) {
            is CastWatchdogFailure.SustainedBuffering -> AppLog.w(TAG) {
                "cast status: sustained buffering watchdog fired after ${failure.timeoutMillis}ms " +
                    "mode=${failure.deliveryMode}"
            }
            is CastWatchdogFailure.LoadStall -> AppLog.w(TAG) {
                "cast status: stall watchdog fired after ${failure.elapsedMillis}ms, " +
                    "${failure.bytesDeliveredThisTick}B served this tick, " +
                    "receiverStatus=${failure.receiverStatus} mode=${failure.deliveryMode}"
            }
        }
        handleReceiverStatus(ReceiverStatus.IDLE, IdleReason.ERROR, selfInitiated = false)
    }

    private fun trackPlayingWindow(status: ReceiverStatus, nowMillis: Long): Long {
        routeHistory.onStatus(status)
        return recovery.onStatus(status, nowMillis)
    }

    private fun currentRouteKind(): CastRouteKind = proxy.routeKind(_state.value.deliveryMode)

    /** [CastRecoveryPolicy.Reload] short-circuits the normal reduce() give-up path entirely - no
     * proxy teardown, no local-playback resume, just a delayed reload of the exact same channel.
     * Anything else (Ignore, GiveUp, or a status this isn't even about) falls through to the
     * existing reducer unchanged. */
    private fun handleReceiverStatus(status: ReceiverStatus, idleReason: IdleReason, selfInitiated: Boolean) {
        // Centralized here so synthetic watchdog IDLE events observe and close the same PLAYING
        // window as callbacks received from the Cast SDK.
        val stablePlayingMillis = trackPlayingWindow(status, android.os.SystemClock.elapsedRealtime())
        val isFailureReason = idleReason == IdleReason.ERROR || idleReason == IdleReason.FINISHED
        val isRecoverableIdle = status == ReceiverStatus.IDLE && isFailureReason
        if (isRecoverableIdle && tryRecover(idleReason, selfInitiated, stablePlayingMillis)) return
        applyResult(CastReceiverStatusReducer.reduce(_state.value, status, idleReason, selfInitiated))
    }

    /** Returns true if a reload was scheduled (the caller must skip the normal give-up reduction);
     * false means [CastRecoveryPolicy] said Ignore or GiveUp, and the normal reducer path - which
     * already handles both of those correctly on its own - should run instead. A GiveUp additionally
     * records this (stream, receiver) pair first, if [IncompatibilityRecordingPolicy] says the
     * failure was genuine rather than transient. */
    private fun tryRecover(
        idleReason: IdleReason,
        selfInitiated: Boolean,
        stablePlayingMillis: Long,
    ): Boolean {
        val channel = activeChannel ?: return false
        val decision = recoveryDecisionFor(idleReason, selfInitiated, stablePlayingMillis)
        if (decision == CastRecoveryDecision.GiveUp) {
            routeHistory.onGiveUp(channel.streamUrl, _state.value.codecIncompatibility != null)
        }
        return if (decision is CastRecoveryDecision.Reload) {
            scheduleReload(channel, decision)
            true
        } else {
            false
        }
    }

    private fun recoveryDecisionFor(
        idleReason: IdleReason,
        selfInitiated: Boolean,
        stablePlayingMillis: Long,
    ): CastRecoveryDecision {
        val isConfirmedIncompatible = _state.value.codecIncompatibility != null
        val decision = recovery.decisionFor(idleReason, isConfirmedIncompatible, selfInitiated)
        AppLog.d(TAG) {
            val mode = _state.value.deliveryMode
            "cast status: state=IDLE idleReason=$idleReason mode=$mode playedMs=$stablePlayingMillis action=$decision"
        }
        return decision
    }

    private fun scheduleReload(channel: CastChannel, decision: CastRecoveryDecision.Reload) {
        val withoutPlayback = CastStatusMessagePolicy.isRecoveringWithoutPlayback(
            everReachedPlaying = routeHistory.everReachedPlaying,
            deliveryMode = _state.value.deliveryMode,
            attempt = decision.attempt,
        )
        _state.update { it.copy(isRecovering = true, recoveringWithoutPlayback = withoutPlayback) }
        recovery.schedule(channel, decision)
    }

    private fun performRecoveryReload(channel: CastChannel) {
        when (_state.value.deliveryMode) {
            CastDeliveryMode.Direct -> loadOnReceiver(
                channel.streamUrl,
                LoadRetryContext(channel.streamUrl, channel.title, channel.userAgent, channel.referrer),
            )
            CastDeliveryMode.Proxy ->
                startProxyAndLoad(channel.streamUrl, channel.title, channel.userAgent, channel.referrer)
        }
    }

    init {
        sessionLifecycle.initialize()
    }

    /**
     * Called by the player whenever its active channel changes, cast or not. While a session is
     * connected this both queues the index as pending (for handoff on disconnect) and starts
     * delivering the new channel to the receiver immediately.
     */
    fun setActiveChannel(channel: CastChannel) {
        activeChannel = channel
        if (currentSession != null) {
            val switchId = channelSwitchSequence.incrementAndGet()
            AppLog.d(TAG) {
                "cast session=${sessionCorrelationId ?: "unknown"} switch=$switchId index=${channel.index}"
            }
            _state.value = CastReceiverStatusReducer.requestChannelSwitch(_state.value, channel.index)
            if (!_state.value.isSessionSuspended) {
                startPlayback(channel.streamUrl, channel.title, channel.userAgent, channel.referrer)
            }
        } else {
            diagnosticCoordinator.scheduleWarmup(channel.streamUrl)
        }
    }

    private fun onSessionActive(session: CastSession, rawSessionId: String? = null) {
        suspensionJob?.cancel()
        val status = session.remoteMediaClient?.mediaStatus
        val keepMedia = currentSession === session && CastSuspensionPolicy.canKeepMedia(
            channelUnchanged = !_state.value.isSessionSuspended || suspendedChannel == activeChannel,
            expectedContentId = expectedReceiverContentId,
            actualContentId = status?.mediaInfo?.contentId,
            status = status?.let { mapPlayerState(it.playerState) } ?: ReceiverStatus.IDLE,
        )
        suspendedChannel = null
        _state.update { it.copy(isSessionSuspended = false, isRecovering = false) }
        val previousSession = currentSession
        if (previousSession !== session) {
            // A callback owned by the previous session may still be in GMS's delivery queue.
            // Detach it and invalidate its PendingResult generation before publishing the new
            // session, even when there is no active channel to issue a replacement load.
            remoteMediaClientCallback?.let { callback ->
                callback.ownerSession.remoteMediaClient?.unregisterCallback(callback)
            }
            loadGeneration.invalidate()
            remoteMediaClientCallback = null
        }
        currentSession = session
        currentReceiverId = session.castDevice?.deviceId
        // A resume callback carries the same CastSession instance. Keep the proxy token stable
        // across that lifecycle edge: changing it would restart the local server and invalidate a
        // receiver URL that may still be in flight. Mint a token only when a genuinely new session
        // is adopted/started (or when a defensive recovery finds an empty token).
        proxy.adoptToken(CastProxySessionTokenPolicy.select(
            previousSession = previousSession,
            session = session,
            currentToken = proxy.token,
            newToken = { UUID.randomUUID().toString() },
        ))
        if (rawSessionId != null) {
            sessionCorrelationId = CorrelationId.from("cast", rawSessionId)
        } else if (sessionCorrelationId == null) {
            sessionCorrelationId = CorrelationId.from("cast", proxy.token)
        }
        channelSwitchSequence.set(0)
        AppLog.d(TAG) { "cast session=${sessionCorrelationId ?: "unknown"} active" }
        // Unregister first: this method runs for onSessionStarted, for onSessionResumed - which can
        // fire more than once for the same session across a suspension - and now for adoption at
        // startup. RemoteMediaClient keeps a list, not a set, so registering twice means every
        // receiver status update is handled twice.
        val callback = remoteMediaClientCallback ?: CastSdkRemoteMediaCallback(
            ownerSession = session,
            onStatusUpdated = ::handleRemoteMediaStatus,
        ).also { remoteMediaClientCallback = it }
        session.remoteMediaClient?.unregisterCallback(callback)
        session.remoteMediaClient?.registerCallback(callback)
        proxy.startEagerly()
        // Covers starting a session from the player while a channel is already open, not just
        // switching channels mid-session: setActiveChannel() records every channel the player
        // opens (including the very first one, via start()), so activeChannel is already set by
        // the time a session connects here even if no switch happened while casting was active.
        if (keepMedia) {
            handleRemoteMediaStatus(session)
        } else {
            activeChannel?.let { startPlayback(it.streamUrl, it.title, it.userAgent, it.referrer) }
        }
    }

    private fun onSessionSuspended() {
        if (_state.value.isSessionSuspended) return
        loadGeneration.invalidate()
        watchdogJob?.cancel()
        recovery.cancel()
        playbackWatchdogs.cancelAll()
        suspendedChannel = activeChannel
        _state.value = CastSuspensionPolicy.suspend(_state.value)
        // Keep the proxy URL/token alive for SDK resume, but never retain locks indefinitely.
        suspensionJob = scope.launch {
            delay(CastSuspensionPolicy.MAX_SUSPENSION_MILLIS)
            if (_state.value.isSessionSuspended) {
                sessionLifecycle.endSession()
                if (_state.value.isSessionSuspended) onSessionInactive()
            }
        }
    }

    private fun onSessionInactive() {
        suspensionJob?.cancel()
        suspensionJob = null
        suspendedChannel = null
        // Invalidate before unregistering/clearing: a PendingResult can complete on another thread
        // while the session is being torn down and must already observe itself as stale.
        loadGeneration.invalidate()
        watchdogJob?.cancel()
        recovery.cancel()
        playbackWatchdogs.cancelAll()
        remoteMediaClientCallback?.let { callback ->
            currentSession?.remoteMediaClient?.unregisterCallback(callback)
        }
        remoteMediaClientCallback = null
        currentSession = null
        currentReceiverId = null
        selfInitiatedTransition = false
        routeHistory.clearAbandonedRoute()
        lastKnownSourceKind = null
        expectedReceiverContentId = null
        routeHistory.reset()
        recovery.reset()
        // See RemuxEffectivenessStore.resetAttemptTracking's doc - its dedupe set otherwise grows
        // for the entire process lifetime, not just one cast session.
        remuxEffectivenessStore.resetAttemptTracking()
        applyResult(CastReceiverStatusReducer.reduce(_state.value, ReceiverStatus.DISCONNECTED))
        sessionCorrelationId = null
    }

    private fun startPlayback(streamUrl: String, title: String, userAgent: String?, referrer: String?) {
        loadGeneration.invalidate()
        proxy.beginPlaybackAttempt()
        watchdogJob?.cancel()
        recovery.cancel()
        diagnosticCoordinator.cancelWarmup()
        playbackWatchdogs.cancelAll()
        recovery.reset()
        routeHistory.reset()
        routeHistory.clearAbandonedRoute()
        val receiverId = currentReceiverId.orEmpty()
        val record = incompatibilityStore.lookup(streamUrl, receiverId)
        val knownIncompatible = IncompatibilityMemoryPolicy.shouldGoStraightToProxy(record, System.currentTimeMillis())
        val mode = CastDeliveryStrategy.initialMode(knownIncompatible)
        lastKnownSourceKind = null
        expectedReceiverContentId = null
        _state.update {
            it.copy(
                deliveryMode = mode,
                codecIncompatibility = null,
                receiverLoadFailed = false,
                isRecovering = false,
                recoveringWithoutPlayback = false,
                proxyUnavailableIpv4Only = false,
                likelyCompatibilityHint = null,
            )
        }

        when (mode) {
            CastDeliveryMode.Direct -> loadDirectWithWatchdog(streamUrl, title, userAgent, referrer)
            CastDeliveryMode.Proxy -> startProxyAndLoad(streamUrl, title, userAgent, referrer)
        }
    }

    private fun loadDirectWithWatchdog(streamUrl: String, title: String, userAgent: String?, referrer: String?) {
        remuxEffectivenessStore.record(CastRouteKind.DIRECT, CastRouteOutcome.ATTEMPTED)
        // A channel already warm (see CastDiagnosticCoordinator) skips the probe entirely - no need
        // to race the watchdog for an answer that's already known. Read BEFORE the first load so
        // its sourceKind informs that load's Cast content-type too (see CastContentType.of) - the
        // whole point of warming the cache - instead of only benefiting later reloads.
        val cached = diagnosticCoordinator.cached(streamUrl)
        if (cached != null) lastKnownSourceKind = cached.sourceKind
        loadOnReceiver(
            streamUrl,
            LoadRetryContext(streamUrl, title, userAgent, referrer),
            scheduleStallWatchdog = false,
        )

        if (_state.value.deliveryMode != CastDeliveryMode.Direct ||
            _state.value.loadPhase == CastLoadPhase.FAILED
        ) return
        val directGeneration = loadGeneration.current
        watchdogJob = scope.launch {
            launch {
                val outcome = if (cached != null) {
                    AppLog.d(TAG) { "cast route: using cached verdict=${cached.verdict} source=${cached.sourceKind}" }
                    cached.verdict to cached.sourceKind
                } else {
                    diagnosticCoordinator.probe(streamUrl)
                }
                val (verdict, sourceKind) = outcome ?: return@launch
                if (!loadGeneration.isCurrent(directGeneration) ||
                    _state.value.deliveryMode != CastDeliveryMode.Direct
                ) return@launch
                lastKnownSourceKind = sourceKind
                handleDiagnosticVerdict(LoadRetryContext(streamUrl, title, userAgent, referrer), verdict, sourceKind)
            }
            delay(WATCHDOG_TIMEOUT_MILLIS)
            if (_state.value.receiverStatus != ReceiverStatus.PLAYING && _state.value.codecIncompatibility == null) {
                fallBackToProxyIfStillDirect(streamUrl, title, userAgent, referrer, "watchdog_timeout")
            }
        }
    }

    private fun handleDiagnosticVerdict(
        context: LoadRetryContext,
        verdict: CastCompatibilityVerdict,
        sourceKind: TsSourceKind,
    ) {
        val decision = CastDeliveryStrategy.onDiagnosticResult(verdict, sourceKind)
        // One self-contained line per routing decision - no URL, just what was found and what it
        // led to, so a field logcat is enough to diagnose a cast failure on its own.
        AppLog.d(TAG) { "cast route: verdict=$verdict source=$sourceKind action=$decision" }
        // Never blocks or reroutes anything by itself - just remembered in case receiverLoadFailed
        // ends up true later, so that message can name a likely cause.
        if (verdict is CastCompatibilityVerdict.LikelyCompatible) {
            _state.update { it.copy(likelyCompatibilityHint = verdict) }
        }
        when (decision) {
            is CastRouteDecision.Blocked -> onRouteBlocked(context.streamUrl, decision.verdict)
            CastRouteDecision.ProxyImmediately -> fallBackToProxyIfStillDirect(
                context.streamUrl,
                context.title,
                context.userAgent,
                context.referrer,
                "raw_ts_compatible",
            )
            CastRouteDecision.NoAction -> Unit
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

    /** Checks IPv4 availability *before* committing to the proxy - the receiver needs the phone's
     * LAN address to fetch from (see [LocalNetworkAddress]), and an IPv6-only network simply has
     * none, no matter how many times this is retried. Bailing out here rather than inside
     * [startProxyAndLoad] leaves [CastPlaybackState.deliveryMode] at Direct - the direct attempt
     * that's already in flight (or already finished on its own) is never cancelled or superseded
     * by a proxy fallback that could never have worked anyway. */
    private fun fallBackToProxyIfStillDirect(
        streamUrl: String,
        title: String,
        userAgent: String?,
        referrer: String?,
        reason: String,
    ) {
        val isStillDirect = _state.value.deliveryMode == CastDeliveryMode.Direct
        val isStillCurrent = StaleChannelGuard.isCurrent(streamUrl, activeChannel?.streamUrl)
        if (!isStillDirect || !isStillCurrent) return
        if (LocalNetworkAddress.currentIpv4Address(appContext) == null) {
            reportProxyUnavailableIpv4Only()
            return
        }
        AppLog.d(TAG) { "Falling back to proxy: $reason" }
        // Only a note that it happened - nothing is persisted until the proxy actually plays this
        // channel, which is what tells a route that cannot work apart from a bad moment on the
        // network. See CastRouteHistory.
        routeHistory.abandonDirect(streamUrl)
        _state.update { it.copy(deliveryMode = CastDeliveryMode.Proxy) }
        startProxyAndLoad(streamUrl, title, userAgent, referrer)
    }

    /** Surfaced instead of silently giving up when no IPv4 LAN address is available at all (an
     * IPv6-only network) - a genuinely unfixable-by-retrying limitation (see
     * docs/PROXY_RULES.md), worth telling the user about explicitly rather than casting quietly
     * failing with no explanation. */
    private fun reportProxyUnavailableIpv4Only() {
        AppLog.w(TAG) { "No IPv4 LAN address available; proxy fallback unavailable on this network" }
        _state.update { it.copy(proxyUnavailableIpv4Only = true) }
    }

    private fun startProxyAndLoad(streamUrl: String, title: String, userAgent: String?, referrer: String?) {
        val host = LocalNetworkAddress.currentIpv4Address(appContext)
        if (host == null) {
            reportProxyUnavailableIpv4Only()
            applyResult(CastProxyFailureReducer.reduce(_state.value))
            return
        }
        val prepared = proxy.prepare(
            host, streamUrl, title, userAgent, referrer, currentSession?.castDevice?.friendlyName,
        ).getOrElse { error ->
            AppLog.w(TAG) { "Cast proxy preparation failed: ${error.javaClass.simpleName}" }
            routeHistory.clearAbandonedRoute()
            applyResult(CastProxyFailureReducer.reduce(_state.value))
            return
        }
        AppLog.d(TAG) { "Proxy fallback loading receiver (resource=${prepared.resourceId})" }
        loadOnReceiver(prepared.localUrl, LoadRetryContext(streamUrl, title, userAgent, referrer))
    }

    private fun loadOnReceiver(
        urlToLoad: String,
        context: LoadRetryContext,
        scheduleStallWatchdog: Boolean = true,
    ) {
        val client = currentSession?.remoteMediaClient ?: return
        val generation = loadGeneration.next()
        selfInitiatedTransition = true
        _state.update { it.copy(loadPhase = CastLoadPhase.LOADING) }
        // Free the phone's own upstream connection BEFORE the receiver (or the proxy's remux
        // reader) needs one - waiting for the load-Success reducer to pause local playback is too
        // late for single-connection IPTV origins, where the still-open local stream blocks the
        // receiver's fetch and the load can never succeed in the first place. A failed/abandoned
        // load still resumes local playback through the existing reducer paths (Failure,
        // receiver error, DISCONNECTED all emit ResumeLocalPlayer).
        emitSideEffect(CastSideEffect.PauseLocalPlayer)
        // Read off activeChannel rather than carried in the context: every path here loads the
        // channel that is active right now (the recovery reload guards that with StaleChannelGuard
        // before it gets this far), and the worst a race could produce is the wrong thumbnail.
        val logoUrl = activeChannel?.logoUrl
        val request = CastMediaLoader.buildRequest(urlToLoad, context.title, lastKnownSourceKind, logoUrl)
        // Whether artwork went out, never the url itself (it is a third-party host, and this line
        // ends up in a shared diagnostics report). Blank-vs-absent is not worth distinguishing here
        // - CastMediaLoader treats both the same - but "we sent none at all" vs "the receiver
        // ignored what we sent" is exactly the split a missing-artwork report needs.
        AppLog.d(TAG) { "cast load: artwork=${!logoUrl.isNullOrBlank()}" }
        val pendingLoad = try {
            // A malformed provider URL or an SDK-side request validation failure must become the
            // same recoverable load failure as a rejected PendingResult, never an exception on the
            // main thread from a channel tap or a delayed recovery callback.
            expectedReceiverContentId = urlToLoad
            client.load(request)
        } catch (error: IllegalArgumentException) {
            handleSdkLoadException(error, generation, context)
            null
        } catch (error: IllegalStateException) {
            handleSdkLoadException(error, generation, context)
            null
        }
        if (pendingLoad == null) return
        // Arm this before registering the callback. A PendingResult that is already complete may
        // invoke setResultCallback synchronously; if that callback starts a newer proxy load first,
        // scheduling this older generation afterwards would cancel the newer load's watchdog.
        if (scheduleStallWatchdog) playbackWatchdogs.watchLoad(generation, context.streamUrl)
        pendingLoad.setResultCallback { result ->
            val loadResult = if (result.status.isSuccess) {
                CastLoadResult.Success
            } else {
                CastLoadResult.Failure("status_${result.status.statusCode}")
            }
            handleLoadResult(generation, result.status.statusCode, loadResult, context)
        }
    }

    private fun handleSdkLoadException(
        error: Exception,
        generation: Long,
        context: LoadRetryContext,
    ) {
        AppLog.w(TAG) { "cast load request rejected: ${error.javaClass.simpleName}" }
        handleLoadResult(
            generation = generation,
            statusCode = -1,
            result = CastLoadResult.Failure("sdk_load_exception"),
            context = context,
        )
    }

    /** First ignores anything from a load a newer request has already superseded - see
     * [loadGeneration] - then, for a same-generation failure, tells apart a status the Cast SDK
     * only reports *because* this request got superseded (see [LoadStatusOutcome.Superseded]) from
     * a genuine failure of this specific request, which still goes through the normal fail path. */
    private fun handleLoadResult(generation: Long, statusCode: Int, result: CastLoadResult, context: LoadRetryContext) {
        if (!loadGeneration.isCurrent(generation)) {
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
        if (result is CastLoadResult.Success) proxy.confirmActiveSession()
        val shouldRetryOnProxy = DirectFailureFallbackPolicy.shouldRetryOnProxy(
            result = result,
            mode = _state.value.deliveryMode,
            isConfirmedIncompatible = _state.value.codecIncompatibility != null,
        )
        if (shouldRetryOnProxy) {
            watchdogJob?.cancel()
            _state.update { it.copy(deliveryMode = CastDeliveryMode.Proxy) }
            startProxyAndLoad(context.streamUrl, context.title, context.userAgent, context.referrer)
            return
        }
        applyResult(CastLoadResultReducer.reduce(_state.value, result))
    }

    /** Ends the active Cast session outright - used by [CastProxyService]'s notification "Stop" action. */
    fun endSession() {
        sessionLifecycle.endSession()
    }

    private fun applyResult(result: CastReducerResult) {
        _state.value = result.state
        for (effect in result.effects) {
            if (effect is CastSideEffect.CloseProxySession) {
                loadGeneration.invalidate()
                watchdogJob?.cancel()
                recovery.cancel()
                playbackWatchdogs.cancelAll()
                proxy.stop()
            }
            emitSideEffect(effect)
        }
    }

    /**
     * Side effects are deliberately bounded one-shot hints, while the state flow above is the
     * source of truth. Coalescing repeated pauses prevents a slow collector from filling the
     * buffer with identical PLAYING notifications and dropping a later hand-back command.
     */
    private fun emitSideEffect(effect: CastSideEffect) {
        if (effect == CastSideEffect.PauseLocalPlayer &&
            lastLocalControlEffect == CastSideEffect.PauseLocalPlayer
        ) {
            return
        }
        if (effect == CastSideEffect.PauseLocalPlayer || effect == CastSideEffect.ResumeLocalPlayer) {
            lastLocalControlEffect = effect
        }
        _sideEffects.tryEmit(effect)
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
