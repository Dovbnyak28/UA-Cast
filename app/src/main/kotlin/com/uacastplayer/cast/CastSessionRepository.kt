package com.uacastplayer.cast

import com.uacastplayer.core.cast.CastRouteKind
import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.IncompatibilityMemoryPolicy
import com.uacastplayer.core.cast.TsSourceKind
import android.content.Context
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.data.cast.IncompatibilityMemoryStore
import com.uacastplayer.data.cast.LocalNetworkAddress
import com.uacastplayer.data.cast.ProxyServer
import com.uacastplayer.data.cast.TsFirstSegmentDiagnostic
import com.uacastplayer.data.prefs.AppPreferences
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
import kotlinx.coroutines.asExecutor
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
 * to the local [ProxyServer] if the receiver isn't PLAYING within 4s (or immediately if
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
    /** One id per user-visible channel playback, shared by every manifest poll and recovery reload
     * inside it. A stable resource SHA is intentionally reused; this is what distinguishes playing
     * that same resource again from one attempt being polled repeatedly. */
    private val proxyPlaybackAttemptId = AtomicLong(0)
    private val proxyServer = ProxyServer(httpClient) { resourceId, route ->
        remuxEffectivenessStore.recordProxyRouteAttemptOnce(proxyPlaybackAttemptId.get(), resourceId, route)
    }
    private val preferences = AppPreferences(appContext)
    private val incompatibilityStore = IncompatibilityMemoryStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var castContext: CastContext? = null
    private var currentSession: CastSession? = null
    private var currentReceiverId: String? = null
    private var activeChannel: CastChannel? = null
    private val diagnosticCoordinator = CastDiagnosticCoordinator(
        scope = scope,
        activeStreamUrl = { activeChannel?.streamUrl },
        diagnose = { streamUrl -> TsFirstSegmentDiagnostic.diagnose(streamUrl, httpClient) },
    )
    private var watchdogJob: Job? = null

    // Set at the start of every proxy fallback attempt (see startProxyAndLoad), used to resolve
    // which RemuxEffectivenessStore bucket (remux vs plain rewrite) a later PLAYING/give-up
    // outcome belongs to - see trackPlayingWindow/tryRecover.
    private var activeProxyResourceId: String? = null

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

    // One token for the whole cast session, not per load - see ProxyServer.ensureStarted. Every
    // channel switch during that session reuses it so the proxy's port/resources/remux session
    // survive the switch instead of being torn down and rebuilt from under the receiver.
    private var proxySessionToken: String = ""

    // Pure attempt/window state is kept outside this Cast SDK adapter. Jobs stay here because they
    // actually issue SDK loads; the decision inputs and their reset rules live together.
    private val recoveryEpisode = CastRecoveryEpisode()
    private var recoveryJob: Job? = null

    // Whether PLAYING has been observed at all this casting episode (across every recovery
    // reload) - see IncompatibilityRecordingPolicy. Reset per channel in startPlayback.
    private var everReachedPlaying = false

    // The channel whose direct attempt was abandoned for the proxy this episode, held until the
    // proxy proves it can play it - at which point the pair is remembered so the next cast skips
    // direct entirely. See DirectRouteMemoryPolicy. Reset per channel in startPlayback.
    private var directRouteAbandonedFor: String? = null
    private var sessionCorrelationId: String? = null
    private val channelSwitchSequence = AtomicLong(0)

    private val _state = MutableStateFlow(CastPlaybackState())
    val state: StateFlow<CastPlaybackState> = _state.asStateFlow()

    private val _sideEffects = MutableSharedFlow<CastSideEffect>(extraBufferCapacity = 8)
    val sideEffects: SharedFlow<CastSideEffect> = _sideEffects.asSharedFlow()

    private val playbackWatchdogs = CastPlaybackWatchdogs(
        scope = scope,
        inputs = CastWatchdogInputs(
            currentGeneration = { loadGeneration.current },
            activeStreamUrl = { activeChannel?.streamUrl },
            receiverStatus = { _state.value.receiverStatus },
            deliveryMode = { _state.value.deliveryMode },
            everReachedPlaying = { everReachedPlaying },
            bytesServedToReceiver = proxyServer::bytesServedToReceiver,
        ),
        onFailure = ::handleWatchdogFailure,
    )

    private val sessionManagerListener = CastSdkSessionListener(::handleSessionEvent)
    private val remoteMediaClientCallback = CastSdkRemoteMediaCallback(::handleRemoteMediaStatus)

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
            is CastSdkSessionEvent.Resumed -> onSessionActive(event.session)
            is CastSdkSessionEvent.ResumeFailed -> AppLog.w(TAG) {
                "cast session=${sessionCorrelationId ?: "unknown"} resume failed error=${event.error}"
            }
            is CastSdkSessionEvent.Suspended -> {
                if (!CastSessionIdentityGuard.isCurrent(event.session, currentSession)) {
                    AppLog.d(TAG) { "cast session: stale suspended callback ignored" }
                    return
                }
                AppLog.d(TAG) { "cast session=${sessionCorrelationId ?: "unknown"} suspended reason=${event.reason}" }
                onSessionInactive()
            }
        }
    }

    private fun handleRemoteMediaStatus() {
        val status = currentSession?.remoteMediaClient?.mediaStatus ?: return
        val receiverStatus = mapPlayerState(status.playerState)
        if (receiverStatus == ReceiverStatus.PLAYING) {
            watchdogJob?.cancel()
            // A delayed recovery can outlive the transient condition that scheduled it.
            recoveryJob?.cancel()
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
        rememberDirectRouteFailureIfProven(status)
        if (status == ReceiverStatus.PLAYING && !everReachedPlaying) {
            everReachedPlaying = true
            remuxEffectivenessStore.record(currentRouteKind(), CastRouteOutcome.REACHED_PLAYING)
        }
        return recoveryEpisode.onStatus(status, nowMillis)
    }

    /** The proxy just played a channel whose direct attempt was abandoned - see
     * [DirectRouteMemoryPolicy] for why that specific pairing, and nothing weaker, is what earns a
     * persisted "skip direct for this pair" record. Cleared either way once the question has been
     * answered for this episode: a second PLAYING transition on the same channel is not new
     * evidence, and the store's own write throttle should not be the thing suppressing it. */
    private fun rememberDirectRouteFailureIfProven(status: ReceiverStatus) {
        val streamUrl = directRouteAbandonedFor ?: return
        if (!DirectRouteMemoryPolicy.provenProxyOnly(_state.value.deliveryMode, status)) return
        directRouteAbandonedFor = null
        // The same staleness question every other deferred continuation here asks: a PLAYING update
        // can arrive for a channel the user has already zapped past, and recording it against
        // whatever is active now would teach the store about the wrong pair entirely.
        val isCurrent = StaleChannelGuard.isCurrent(streamUrl, activeChannel?.streamUrl)
        val receiverId = currentReceiverId
        if (isCurrent && receiverId != null) {
            AppLog.d(TAG) { "cast route: direct never played and the proxy did - remembering this pair" }
            incompatibilityStore.record(streamUrl, receiverId)
        }
    }

    /** See [RemuxEffectivenessStore]/[activeProxyResourceId]: which bucket the current attempt's
     * eventual REACHED_PLAYING or FAILED outcome belongs in. */
    private fun currentRouteKind(): CastRouteKind = when (_state.value.deliveryMode) {
        CastDeliveryMode.Direct -> CastRouteKind.DIRECT
        CastDeliveryMode.Proxy -> {
            val resourceId = activeProxyResourceId
            if (resourceId != null && proxyServer.wasRemuxed(resourceId)) {
                CastRouteKind.PROXY_REMUX
            } else {
                CastRouteKind.PROXY_REWRITE
            }
        }
    }

    /** [CastRecoveryPolicy.Reload] short-circuits the normal reduce() give-up path entirely - no
     * proxy teardown, no local-playback resume, just a delayed reload of the exact same channel.
     * Anything else (Ignore, GiveUp, or a status this isn't even about) falls through to the
     * existing reducer unchanged. */
    private fun handleReceiverStatus(status: ReceiverStatus, idleReason: IdleReason, selfInitiated: Boolean) {
        // Centralized here so synthetic watchdog IDLE events observe and close the same PLAYING
        // window as callbacks received from the Cast SDK.
        val stablePlayingMillis = trackPlayingWindow(status, System.currentTimeMillis())
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
            recordIfGenuinelyIncompatible(channel.streamUrl)
            // A route that already reached PLAYING once got its REACHED_PLAYING credit in
            // trackPlayingWindow - a later give-up on the same episode is a reliability concern,
            // not a routing-never-worked one, so only an attempt that never played counts as FAILED.
            if (!everReachedPlaying) remuxEffectivenessStore.record(currentRouteKind(), CastRouteOutcome.FAILED)
        }
        return if (decision is CastRecoveryDecision.Reload) {
            scheduleReload(channel, decision)
            true
        } else {
            false
        }
    }

    private fun recordIfGenuinelyIncompatible(streamUrl: String) {
        val isConfirmedIncompatible = _state.value.codecIncompatibility != null
        val shouldRecord = IncompatibilityRecordingPolicy.shouldRecord(isConfirmedIncompatible, everReachedPlaying)
        if (!shouldRecord) return
        currentReceiverId?.let { incompatibilityStore.record(streamUrl, it) }
    }

    private fun recoveryDecisionFor(
        idleReason: IdleReason,
        selfInitiated: Boolean,
        stablePlayingMillis: Long,
    ): CastRecoveryDecision {
        val isConfirmedIncompatible = _state.value.codecIncompatibility != null
        val decision = recoveryEpisode.decisionFor(idleReason, isConfirmedIncompatible, selfInitiated)
        AppLog.d(TAG) {
            val mode = _state.value.deliveryMode
            "cast status: state=IDLE idleReason=$idleReason mode=$mode playedMs=$stablePlayingMillis action=$decision"
        }
        return decision
    }

    private fun scheduleReload(channel: CastChannel, decision: CastRecoveryDecision.Reload) {
        recoveryEpisode.scheduled(decision)
        val withoutPlayback = CastStatusMessagePolicy.isRecoveringWithoutPlayback(
            everReachedPlaying = everReachedPlaying,
            deliveryMode = _state.value.deliveryMode,
            attempt = decision.attempt,
        )
        _state.update { it.copy(isRecovering = true, recoveringWithoutPlayback = withoutPlayback) }
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            delay(decision.backoffMillis)
            if (StaleChannelGuard.isCurrent(channel.streamUrl, activeChannel?.streamUrl)) performRecoveryReload(channel)
        }
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
        initCastContext()
    }

    /**
     * Off the main thread, deliberately. This singleton is constructed from [AppViewModel]'s
     * property initializers, which run inside `MainActivity.onCreate` before `setContent` - so the
     * blocking `CastContext.getSharedInstance(Context)` this replaces sat squarely on the cold-start
     * critical path, spinning up the Play Services Cast module and reflectively resolving
     * `CastOptionsProvider` while the first frame waited on it.
     *
     * The listener registration still lands on the main thread (that is where `Task` callbacks are
     * delivered by default, and `SessionManager` expects it). Nothing reads [castContext] before
     * then except [endSession], and there can be no session to end until the framework is up. If
     * the Cast button is composed before this settles, `CastButtonFactory` initializes the same
     * shared instance itself and the two converge on it.
     *
     * Measured on a Pixel 10 Pro emulator (API 37, x86_64), debug build, median of 5 cold starts:
     * the blocking call itself cost ~120ms on the main thread, and `MainActivity.onCreate` went
     * from 349ms (range 341-404) to 303ms (range 294-305) - so this also removes the long tail, not
     * just the median. `am start -W` TotalTime is NOT sensitive to this and shows no change: it
     * measures to the splash window's first frame, which the system draws before this work ever
     * runs. The framework itself finishes resolving 160-900ms in, well after onCreate returns.
     */
    // Play Services being missing/outdated/misconfigured can surface as several different
    // exception types - all of them mean "no cast support on this device", not a crash. Both the
    // synchronous throw and the Task's own failure path are covered.
    @Suppress("TooGenericExceptionCaught")
    private fun initCastContext() {
        try {
            CastContext.getSharedInstance(appContext, ioDispatcher.asExecutor())
                .addOnSuccessListener { context ->
                    castContext = context
                    context.sessionManager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
                    adoptSessionAlreadyRunning(context.sessionManager)
                }
                .addOnFailureListener { e ->
                    AppLog.w(TAG) { "Cast context unavailable: ${e.javaClass.simpleName}" }
                }
        } catch (e: Exception) {
            AppLog.w(TAG) { "Cast context unavailable: ${e.javaClass.simpleName}" }
        }
    }

    /**
     * Picks up a session that was already connected before this repository was listening.
     *
     * [SessionManager] reports transitions, not state: a listener registered after a session is up
     * hears nothing until that session ends. Two ordinary situations land there.
     *
     * The first is process death. A cast session lives in Play Services, not in this app, and
     * survives the app being killed in the background - so reopening the app builds a repository
     * that believes nothing is casting while a receiver is still connected. Everything downstream
     * follows that belief: `LocalPlaybackPolicy` only holds the local player back while
     * `isCasting`, so the restored channel starts playing out of the phone's speaker into a room
     * where the TV is also connected, and the Cast button - which reads the framework directly -
     * contradicts the rest of the UI.
     *
     * The second needs no process death at all, and this file already describes it: resolving the
     * shared [CastContext] is deliberately off the main thread, and "if the Cast button is composed
     * before this settles, `CastButtonFactory` initializes the same shared instance itself". A
     * session started in that window connects before the line above runs.
     *
     * Guarded on [currentSession] rather than adopting unconditionally, because adoption is not
     * free: it mints a fresh proxy token and starts playback, both of which would be wrong for a
     * session this repository is already driving.
     */
    private fun adoptSessionAlreadyRunning(sessionManager: SessionManager) {
        if (currentSession != null) return
        sessionManager.currentCastSession
            ?.takeIf { it.isConnected }
            ?.let { session ->
                AppLog.d(TAG) { "Adopting a cast session that was already connected" }
                onSessionActive(session)
            }
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
            startPlayback(channel.streamUrl, channel.title, channel.userAgent, channel.referrer)
        } else {
            diagnosticCoordinator.scheduleWarmup(channel.streamUrl)
        }
    }

    private fun onSessionActive(session: CastSession, rawSessionId: String? = null) {
        val previousSession = currentSession
        if (previousSession !== session) {
            // A callback owned by the previous session may still be in GMS's delivery queue.
            // Detach it and invalidate its PendingResult generation before publishing the new
            // session, even when there is no active channel to issue a replacement load.
            previousSession?.remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
            loadGeneration.invalidate()
        }
        currentSession = session
        currentReceiverId = session.castDevice?.deviceId
        proxySessionToken = UUID.randomUUID().toString()
        if (rawSessionId != null) {
            sessionCorrelationId = CorrelationId.from("cast", rawSessionId)
        } else if (sessionCorrelationId == null) {
            sessionCorrelationId = CorrelationId.from("cast", proxySessionToken)
        }
        channelSwitchSequence.set(0)
        AppLog.d(TAG) { "cast session=${sessionCorrelationId ?: "unknown"} active" }
        // Unregister first: this method runs for onSessionStarted, for onSessionResumed - which can
        // fire more than once for the same session across a suspension - and now for adoption at
        // startup. RemoteMediaClient keeps a list, not a set, so registering twice means every
        // receiver status update is handled twice.
        session.remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
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
        CastProxyOperation.run { ensureProxyStarted(host) }
            .onFailure { error ->
                // This runs from a Cast SDK callback on main. A failed local socket bind costs the
                // optimisation only; direct receiver playback must still get its chance.
                AppLog.w(TAG) { "Eager Cast proxy startup failed: ${error.javaClass.simpleName}" }
                proxyServer.stop()
            }
    }

    private fun ensureProxyStarted(host: String) {
        proxyServer.ensureStarted(
            sessionToken = proxySessionToken,
            host = host,
            remuxEnabled = preferences.rawTsRemuxEnabled,
            // Passed rather than left at its default so that turning the remux escape hatch off
            // restores exactly the behaviour it always restored on this path - the two used to be
            // one flag (see ProxyServer.unwrapWrapperPlaylists). Untying them for Chromecast is
            // arguably the better default, since an unwrapped stream would be passed through
            // rather than handed over as a manifest with an endless "segment" in it, but that is a
            // change to a receiver this cannot be tested against.
            unwrapWrapperPlaylists = preferences.rawTsRemuxEnabled,
        )
    }

    private fun onSessionInactive() {
        // Invalidate before unregistering/clearing: a PendingResult can complete on another thread
        // while the session is being torn down and must already observe itself as stale.
        loadGeneration.invalidate()
        watchdogJob?.cancel()
        recoveryJob?.cancel()
        playbackWatchdogs.cancelAll()
        currentSession?.remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
        currentSession = null
        currentReceiverId = null
        selfInitiatedTransition = false
        activeProxyResourceId = null
        directRouteAbandonedFor = null
        lastKnownSourceKind = null
        everReachedPlaying = false
        recoveryEpisode.reset()
        // See RemuxEffectivenessStore.resetAttemptTracking's doc - its dedupe set otherwise grows
        // for the entire process lifetime, not just one cast session.
        remuxEffectivenessStore.resetAttemptTracking()
        applyResult(CastReceiverStatusReducer.reduce(_state.value, ReceiverStatus.DISCONNECTED))
        sessionCorrelationId = null
    }

    private fun startPlayback(streamUrl: String, title: String, userAgent: String?, referrer: String?) {
        proxyPlaybackAttemptId.incrementAndGet()
        watchdogJob?.cancel()
        recoveryJob?.cancel()
        diagnosticCoordinator.cancelWarmup()
        playbackWatchdogs.cancelAll()
        recoveryEpisode.reset()
        everReachedPlaying = false
        directRouteAbandonedFor = null
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

        watchdogJob = scope.launch {
            launch {
                val outcome = if (cached != null) {
                    AppLog.d(TAG) { "cast route: using cached verdict=${cached.verdict} source=${cached.sourceKind}" }
                    cached.verdict to cached.sourceKind
                } else {
                    diagnosticCoordinator.probe(streamUrl)
                }
                val (verdict, sourceKind) = outcome ?: return@launch
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
        // network. See DirectRouteMemoryPolicy / rememberDirectRouteFailureIfProven.
        directRouteAbandonedFor = streamUrl
        remuxEffectivenessStore.record(CastRouteKind.DIRECT, CastRouteOutcome.FAILED)
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
            return
        }
        val hadProxyOwner = activeProxyResourceId != null
        val prepared = CastProxyOperation.run {
            ensureProxyStarted(host)
            val resourceId = proxyServer.registerPlaylist(streamUrl, userAgent, referrer)
            PreparedCastProxy(resourceId, proxyServer.buildLocalUrl(resourceId))
        }.getOrElse { error ->
            AppLog.w(TAG) { "Cast proxy preparation failed: ${error.javaClass.simpleName}" }
            proxyServer.stop()
            if (hadProxyOwner) applyProxyLifecycle(ProxyLifecycleEvent.STOPPED)
            activeProxyResourceId = null
            directRouteAbandonedFor = null
            applyResult(CastProxyFailureReducer.reduce(_state.value))
            return
        }
        applyProxyLifecycle(
            event = ProxyLifecycleEvent.STARTED,
            channelTitle = title,
            receiverName = currentSession?.castDevice?.friendlyName,
        )
        activeProxyResourceId = prepared.resourceId
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
        if (!_sideEffects.tryEmit(CastSideEffect.PauseLocalPlayer)) {
            AppLog.w(TAG) { "Dropped cast side effect, no buffer space: PauseLocalPlayer" }
        }
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
        val pendingLoad = client.load(request)
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
        if (result is CastLoadResult.Success) proxyServer.confirmActiveSession()
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
        castContext?.sessionManager?.endCurrentSession(true)
    }

    private fun applyProxyLifecycle(
        event: ProxyLifecycleEvent,
        channelTitle: String? = null,
        receiverName: String? = null,
    ) {
        when (ProxySessionPolicy.commandFor(event)) {
            ProxyServiceCommand.StartForeground ->
                CastProxyService.start(appContext, channelTitle.orEmpty(), receiverName.orEmpty())
            ProxyServiceCommand.StopForeground -> CastProxyService.stop(appContext)
        }
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
