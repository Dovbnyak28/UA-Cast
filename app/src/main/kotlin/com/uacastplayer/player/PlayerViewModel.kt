package com.uacastplayer.player

import android.app.Application
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.uacastplayer.BuildConfig
import com.uacastplayer.cast.CastChannel
import com.uacastplayer.cast.CastStatusMessagePolicy
import com.uacastplayer.cast.CastSessionRepository
import com.uacastplayer.cast.CastSideEffect
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.dlna.DlnaSessionRepository
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.log.AppLog
import com.uacastplayer.R
import com.uacastplayer.playlist.M3uChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the single ExoPlayer instance for a player session. Scoped to the host Activity (see
 * [com.uacastplayer.ui.player.PlayerHost]) so exactly one instance survives mini/fullscreen
 * toggles, reopens and configuration changes; [releasePlayback] frees the loaded stream without
 * destroying the instance itself, which only happens when the Activity is actually destroyed.
 */
@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    // Registered before anything this ViewModel owns (ExoPlayer, MediaSession) is constructed, so
    // the guard in init sees a second live instance as early as possible. See liveInstances.
    private val liveInstanceCount: Int = liveInstances.incrementAndGet()

    private val preferences = AppPreferences(application)
    private val networkGate = PlayerNetworkGate(application)

    private val dataSourceFactory = PlayerDataSourceFactory.create(application)

    // handleAudioFocus=true lets ExoPlayer request/abandon audio focus and duck/pause on its own
    // for calls and other apps' audio; setHandleAudioBecomingNoisy pauses when headphones/BT
    // disconnect so playback doesn't suddenly blast through the speaker. Both only ever touch this
    // local player - while casting, switchToIndexImmediate() keeps it stopped (see
    // LocalPlaybackPolicy) instead of playing, so it never holds focus and a focus loss elsewhere
    // has nothing local to pause; the cast receiver's playback state is owned entirely by
    // CastSessionRepository and is unreachable from this player's focus/noisy callbacks.
    private val exoPlayer: ExoPlayer = PlayerEngineFactory.create(
        context = application,
        dataSourceFactory = dataSourceFactory,
        bufferSize = preferences.effectiveBufferSize,
    )

    val player: Player get() = exoPlayer

    private var castArtworkUrlFor: (M3uChannel) -> String? = DEFAULT_CAST_ARTWORK
    private val sessionStateMachine = PlayerSessionStateMachine()
    private var retryJob: Job? = null
    private var stallRecoveryJob: Job? = null

    var wrapAroundEnabled: Boolean = preferences.wrapAroundEnabled
    var autoSkipDeadEnabled: Boolean = preferences.autoSkipDeadEnabled
    private var autoSkipCancelledForSession: Boolean = false

    private val castRepository = CastSessionRepository.getInstance(application)
    private var isCasting: Boolean = false

    // DLNA is a second, independent cast target (see dlna/DlnaSessionRepository) - a receiver only
    // ever reachable one way or the other, never both, but nothing enforces that here: as far as
    // this ViewModel is concerned there are simply two reasons the local player might have to stand
    // down. isRemoteCasting is what the rest of the class checks, so a new target would only need
    // to be OR'd in there.
    private val dlnaRepository = DlnaSessionRepository.getInstance(application)
    private var isDlnaCasting: Boolean = false
    val dlnaState: StateFlow<DlnaConnectionState> = dlnaRepository.state

    private val isRemoteCasting: Boolean get() = isCasting || isDlnaCasting

    private val _uiState = MutableStateFlow(PlayerUiState(resizeMode = preferences.playerResizeMode))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    val dlna = PlayerDlnaController(dlnaRepository) { _uiState.value.currentChannel }
    val tracks = PlayerTrackController(exoPlayer)
    val navigation = PlayerNavigationController(
        channel = ChannelNavigationContext(
            scope = viewModelScope,
            sessionStateMachine = sessionStateMachine,
            wrapAroundEnabled = { wrapAroundEnabled },
            switchImmediately = ::switchToIndexImmediate,
        ),
        canSeek = { _uiState.value.canSeek },
        player = exoPlayer,
        resizeMode = ResizeModeContext(
            preferences = preferences,
            current = { _uiState.value.resizeMode },
            update = { mode -> _uiState.update { it.copy(resizeMode = mode) } },
        ),
    )
    // A full MediaSessionService is intentionally out of scope for live TV; this optional bridge
    // simply exposes the Activity-scoped player to headset/watch/system controls.
    private val mediaSession = PlayerMediaSessionFactory.create(
        context = application,
        player = exoPlayer,
        onNext = navigation::requestNext,
        onPrevious = navigation::requestPrevious,
    )
    private val trackMapper = PlayerTrackMapper { index ->
        getApplication<Application>().getString(R.string.player_track_unknown, index)
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                sessionStateMachine.onPlaybackConfirmed()
                // Natural recovery can beat the scheduled backoff. Leaving that job alive would
                // seek/re-prepare a stream that is healthy again a few seconds later.
                cancelPendingStallRecovery()
            }
            _uiState.update {
                it.copy(
                    isPlaying = isPlaying,
                    autoSkipRecovery = if (isPlaying) null else it.autoSkipRecovery,
                )
            }
            PlaybackActivity.setActive(isPlaying || isRemoteCasting)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) cancelPendingStallRecovery()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            updateSeekability()
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackError(error)
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateBadgesAndTrackLists(tracks)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _uiState.update { it.copy(videoSize = videoSize) }
        }
    }

    init {
        if (PlayerInstanceGuard.isLeak(liveInstanceCount)) {
            val leak = IllegalStateException(
                "Second PlayerViewModel created while another is alive - this leaks an ExoPlayer",
            )
            // Logged directly rather than via AppLog (which compiles out in release) so this
            // production-only regression still shows up in release logs; in debug we also crash
            // loudly so it can never be missed during development.
            Log.e(TAG, leak.message, leak)
            if (BuildConfig.DEBUG) throw leak
        }
        exoPlayer.addListener(listener)
        exoPlayer.playWhenReady = true

        viewModelScope.launch {
            castRepository.state.collect { state ->
                isCasting = state.isSessionConnected
                _uiState.update {
                    it.copy(
                        isCasting = isCasting,
                        castStatusMessage = CastStatusMessagePolicy.messageFor(state),
                    )
                }
                updateSeekability()
                PlaybackActivity.setActive(_uiState.value.isPlaying || isRemoteCasting)
            }
        }
        viewModelScope.launch {
            castRepository.sideEffects.collect { effect -> handleCastSideEffect(effect) }
        }
        viewModelScope.launch {
            dlnaRepository.state.collect { state -> handleDlnaStateChange(state) }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(STALL_SAMPLE_INTERVAL_MILLIS)
                sampleForStall()
            }
        }
    }

    /**
     * DLNA has no receiver-status callbacks to reduce over the way Cast does (see
     * [handleCastSideEffect] and `docs/DLNA.md`), so local playback is driven straight off the
     * connected/disconnected edge instead.
     *
     * stop(), not pause(), for exactly the reason [CastSideEffect.PauseLocalPlayer] gives: a paused
     * live stream keeps its upstream connection, and IPTV origins routinely allow one connection per
     * account - while the phone holds that slot the proxy feeding the renderer can never get its
     * own, so casting starves. The media item survives stop(), so prepare()+play() below restores
     * local playback fully.
     */
    private fun handleDlnaStateChange(state: DlnaConnectionState) {
        val isConnected = state.connectedDevice != null
        if (isConnected == isDlnaCasting) return
        isDlnaCasting = isConnected
        if (isConnected) {
            cancelLocalRecoveryForRemotePlayback()
            exoPlayer.stop()
        } else if (
            // isCasting, not "nothing else is connected": a Chromecast session can still be running,
            // and resuming here would put the same stream on the phone and the receiver at once -
            // audible twice, and the phone takes the origin's one allowed connection, starving the
            // receiver that was playing fine a second ago. See LocalPlaybackPolicy.
            LocalPlaybackPolicy.shouldResumeAfterDisconnect(
                isChromecastActive = isCasting,
                isDlnaActive = false,
            )
        ) {
            resumeLocalPlayback()
        }
        updateSeekability()
        PlaybackActivity.setActive(_uiState.value.isPlaying || isRemoteCasting)
    }

    /**
     * Takes the stream back onto the phone after a receiver let go of it - deferred if the app is
     * not on screen to take it.
     *
     * Both cast paths end here (see [handleCastSideEffect] and [handleDlnaStateChange]), and both
     * used to `prepare()` + `play()` on the spot. That is right when the user is looking at the
     * player and wrong in the case that reaches it just as often: casting and then putting the
     * phone away is, in [BackgroundPlaybackPolicy]'s own words, "the normal way to use a cast" - so
     * the policy deliberately does not pause on backgrounding while a cast is running, and the
     * phone is off screen with nothing paused when the TV is switched off. Playing then produced
     * exactly what that policy exists to prevent, arriving by a door it never sees: a live stream
     * out of a stopped activity, several megabits a minute, a wake lock and a Wi-Fi lock held, and
     * audio out of the speaker of a phone in somebody's pocket - with no notification to stop it,
     * because this app has no MediaSessionService.
     *
     * Deferred rather than dropped. Dropping it would leave the user returning to a player that is
     * simply dead - the media item is set but was never prepared (see [switchToIndexImmediate],
     * which skips prepare while casting) - so the resume is owed and [onReturnToForeground] pays it.
     *
     * While casting, [switchToIndexImmediate] skips `prepare()` for the current channel so the
     * phone is not buffering the same stream in parallel with the receiver. The media item is still
     * set, just never prepared, which is why `prepare()` has to come before `play()` here.
     */
    @VisibleForTesting
    internal fun resumeLocalPlayback() {
        if (isInBackground) {
            AppLog.d(TAG) { "Cast ended off screen - local playback owed until the app is back" }
            resumeLocalWhenForeground = true
            return
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    private fun handleCastSideEffect(effect: CastSideEffect) {
        when (effect) {
            // stop(), not pause(): a paused live stream keeps its upstream connection (and often
            // keeps buffering), and IPTV providers routinely enforce ONE connection per account -
            // as long as the phone holds that slot, the receiver's own fetch (direct mode) or the
            // proxy's remux reader can never get a working connection, so casting starves forever.
            // The media item survives stop(), and ResumeLocalPlayer's prepare()+play() below fully
            // recovers from it. sampleForStall can't fight this: it bails while isCasting.
            CastSideEffect.PauseLocalPlayer -> {
                cancelLocalRecoveryForRemotePlayback()
                exoPlayer.stop()
            }
            CastSideEffect.ResumeLocalPlayer -> if (
                // A DLNA renderer can still be playing: the two targets are connected and dropped
                // independently, so "the cast ended" is not "nothing is playing remotely". Resuming
                // anyway puts the same stream on the phone and the renderer at once, and the phone
                // takes the origin's one allowed connection - the renderer then starves. See
                // LocalPlaybackPolicy.
                //
                // isChromecastActive is passed as false rather than read from isCasting: this
                // effect *is* the end of the cast, and it arrives on a different flow from the one
                // that clears that flag, with no ordering between them. Reading it here would
                // sometimes see a session that is already over and leave the phone silent.
                LocalPlaybackPolicy.shouldResumeAfterDisconnect(
                    isChromecastActive = false,
                    isDlnaActive = isDlnaCasting,
                )
            ) {
                resumeLocalPlayback()
            }
            is CastSideEffect.ApplyPendingChannelSwitch -> switchToIndexImmediate(effect.index)
            is CastSideEffect.RecordIncompatibility ->
                AppLog.d(TAG) { "Cast incompatibility recorded: ${effect.reason}" }
            CastSideEffect.CloseProxySession -> Unit // CastSessionRepository owns and closes the proxy itself.
        }
    }

    /** [castArtworkUrlFor] resolves the logo a Cast receiver is shown for a channel; see
     * [com.uacastplayer.AppViewModel.castArtworkUrlFor] for why it is a function and not a value.
     * The default is the channel's own `tvg-logo`, i.e. what this class did before anything richer
     * was passed in. */
    fun start(
        channels: List<M3uChannel>,
        startIndex: Int,
        castArtworkUrlFor: (M3uChannel) -> String? = DEFAULT_CAST_ARTWORK,
    ) {
        autoSkipCancelledForSession = false
        if (channels.isEmpty()) {
            // The Activity-scoped ViewModel outlives PlayerHost. An empty replacement must not
            // leave the previous media item, codecs, retry jobs, and UI channel alive inside it.
            releasePlayback()
            return
        }
        this.castArtworkUrlFor = castArtworkUrlFor
        sessionStateMachine.start(
            channels = channels,
            startIndex = startIndex.coerceIn(channels.indices),
            wrapAround = wrapAroundEnabled,
        )?.let(::applyChannelSwitch)
    }

    private fun switchToIndexImmediate(index: Int) {
        val transition = sessionStateMachine.switchTo(index, wrapAroundEnabled) ?: return
        applyChannelSwitch(transition)
    }

    /** Executes a pure [PlayerSessionStateMachine.ChannelSwitch] against Media3 and the receivers. */
    private fun applyChannelSwitch(
        transition: PlayerSessionStateMachine.ChannelSwitch,
        autoSkipRecovery: AutoSkipRecoveryState? = null,
    ) {
        // A third stale-job class alongside stallRecoveryJob/retryJob below: requestSwitch's own
        // debounce cancels only a *previous* debounce, not a switch that lands through some other
        // path (start() loading a fresh playlist, a cast-driven ApplyPendingChannelSwitch, an
        // auto-skip to the next live channel) while its delay is still running. Left alive, that
        // debounce fires later against whatever `channels`/`index` mean by then - which, after a
        // fresh start(), is a different playlist than the one the tap was made against.
        navigation.cancelPendingSwitch()
        cancelPendingStallRecovery()
        retryLocalWhenForeground = false
        // The other per-channel recovery budget, reset here for the same reason its stall sibling
        // above is: RetryState counts attempts at playing *this* channel (see PlaybackRetryPolicy,
        // and start(), which resets it too), and carrying a spent one into the next channel gave
        // that channel no attempts at all. A channel abandoned after burning its four tries left
        // the budget at MAX, so the next channel's very first network error went straight to
        // GiveUp - marked dead in deadIndices, and with auto-skip on, skipped to another channel
        // that inherited the same empty budget and died on its first error too. One broken channel
        // could bury a run of working ones, which is the same cascade DeadChannelPolicy's network
        // check exists to prevent, arriving by the one door that check does not cover.
        // And its pending work. retryJob holds a delayed exoPlayer.prepare() aimed at the channel
        // being left; releasePlayback and onCleared both cancel it, this path did not. Left alive
        // it re-prepares the *new* channel mid-play, which on a live stream is a rebuffer on a
        // channel that was working.
        retryJob?.cancel()
        val channel = transition.channel
        preferences.lastWatchedChannelKey = FavoriteKey.of(channel)
        dataSourceFactory.setChannelHeaders(channel.userAgent, channel.referrer)
        exoPlayer.setMediaItem(MediaItemFactory.forChannel(channel.streamUrl))
        if (LocalPlaybackPolicy.shouldPrepareLocally(isRemoteCasting)) {
            exoPlayer.prepare()
        } else {
            // The media item is still set so ResumeLocalPlayer (on cast disconnect) can prepare
            // it fresh once local playback resumes - just not prepared right now.
            exoPlayer.stop()
        }
        castRepository.setActiveChannel(
            CastChannel(
                index = transition.index,
                streamUrl = channel.streamUrl,
                title = channel.displayName,
                userAgent = channel.userAgent,
                referrer = channel.referrer,
                logoUrl = castArtworkUrlFor(channel),
            ),
        )
        // No-op unless a DLNA renderer is actually connected. Unconditional on purpose: the local
        // player has already stood down for a remote target above, so without this a channel switch
        // during a DLNA cast would change nothing anywhere - the TV keeps the old channel and the
        // phone plays nothing.
        dlnaRepository.setActiveChannel(channel.streamUrl, channel.displayName)
        _uiState.update {
            it.copy(
                currentChannel = channel,
                isBuffering = true,
                badges = PlaybackBadgesState(),
                nextChannelsPreview = transition.preview,
                fatalError = false,
                hasPreviousChannel = transition.hasPreviousChannel,
                isRecoveringPlayback = false,
                stallRecoveryAttempt = 0,
                autoSkipRecovery = autoSkipRecovery,
            )
        }
    }

    /** Actions exposed by the failure/recovery card; both are safe after a stale recomposition. */
    fun retryCurrentChannel() {
        sessionStateMachine.retryCurrent(wrapAroundEnabled)?.let(::applyChannelSwitch)
    }

    fun cancelAutoSkipRecovery() {
        // Session-only: cancelling recovery must not silently rewrite the persisted Settings value.
        autoSkipCancelledForSession = true
        _uiState.update { it.copy(autoSkipRecovery = null) }
    }

    private fun handlePlaybackError(error: PlaybackException) {
        val errorType = PlayerErrorClassifier.classify(error)
        when (
            val effect = sessionStateMachine.onPlaybackError(
                errorType = errorType,
                nowMillis = System.currentTimeMillis(),
                hasNetwork = networkGate.hasValidatedNetwork(),
                autoSkipDead = autoSkipDeadEnabled && !autoSkipCancelledForSession,
                wrapAround = wrapAroundEnabled,
            )
        ) {
            is PlayerSessionStateMachine.PlaybackFailureEffect.RecoverLiveWindow -> {
                AppLog.d(TAG) {
                    "Recovering from BehindLiveWindowException " +
                        "(attempt ${effect.attemptInWindow} in the last 60s)"
                }
                exoPlayer.seekToDefaultPosition()
                exoPlayer.prepare()
            }
            is PlayerSessionStateMachine.PlaybackFailureEffect.Retry -> {
                retryJob?.cancel()
                retryJob = viewModelScope.launch {
                    delay(effect.delayMillis)
                    performScheduledPlaybackRetry()
                }
            }
            PlayerSessionStateMachine.PlaybackFailureEffect.RetryWhenNetworkAvailable -> {
                AppLog.d(TAG) { "No network: retrying the same channel instead of marking it dead" }
                retryJob?.cancel()
                retryJob = viewModelScope.launch {
                    networkGate.awaitValidatedNetworkOrTimeout(DeadChannelPolicy.NO_NETWORK_RETRY_MILLIS)
                    performScheduledPlaybackRetry()
                }
            }
            is PlayerSessionStateMachine.PlaybackFailureEffect.SwitchChannel ->
                applyChannelSwitch(
                    transition = effect.transition,
                    autoSkipRecovery = AutoSkipRecoveryState(
                        skippedChannels = effect.skippedChannels,
                        totalChannels = effect.totalChannels,
                    ),
                )
            PlayerSessionStateMachine.PlaybackFailureEffect.Fatal -> {
                _uiState.update {
                    it.copy(fatalError = true, isBuffering = false, autoSkipRecovery = null)
                }
            }
        }
    }

    /**
     * Catches a "silent" stall - a stream that stopped delivering bytes without ever breaking the
     * connection, so [onPlayerError] never fires - via periodic sampling (see the init block).
     * Skipped entirely while casting (the local player isn't the one actually playing then, see
     * [LocalPlaybackPolicy]) or while paused (nothing to stall).
     *
     * Recovery never gives up on a live channel by itself (see [StallRetryPolicy]'s KDoc for why
     * the old 30s-cooldown-then-give-up behavior was actively self-defeating) - it only escalates
     * to slower, then eventually 30s-steady, retries. [giveUpOnCurrentChannel] is reserved for real
     * [androidx.media3.common.PlaybackException]s classified via [PlayerErrorClassifier].
     */
    private fun sampleForStall() {
        if (isRemoteCasting || !sessionStateMachine.hasCurrentChannel || !exoPlayer.playWhenReady) return

        val tick = StallDetectionPolicy.Tick(
            nowMillis = System.currentTimeMillis(),
            positionMs = exoPlayer.currentPosition,
            phase = when (exoPlayer.playbackState) {
                Player.STATE_BUFFERING -> StallDetectionPolicy.PlaybackPhase.BUFFERING
                Player.STATE_READY -> StallDetectionPolicy.PlaybackPhase.READY
                else -> StallDetectionPolicy.PlaybackPhase.OTHER
            },
            playWhenReady = exoPlayer.playWhenReady,
            isLive = exoPlayer.isCurrentMediaItemLive,
        )
        val threshold = StallDetectionPolicy.thresholdMillisFor(preferences.effectiveBufferSize)
        when (val effect = sessionStateMachine.onStallTick(tick, threshold)) {
            PlayerSessionStateMachine.StallEffect.None -> Unit
            PlayerSessionStateMachine.StallEffect.ClearRecoveryIndicator -> {
                if (!_uiState.value.isRecoveringPlayback) return
                _uiState.update { it.copy(isRecoveringPlayback = false, stallRecoveryAttempt = 0) }
            }
            is PlayerSessionStateMachine.StallEffect.ScheduleRecovery -> {
                _uiState.update {
                    it.copy(isRecoveringPlayback = true, stallRecoveryAttempt = effect.attempt)
                }
                AppLog.d(TAG) {
                    "Recovering from a silent stall: attempt ${effect.attempt}," +
                        " retrying in ${effect.delayMillis}ms"
                }
                stallRecoveryJob?.cancel()
                stallRecoveryJob = viewModelScope.launch {
                    delay(effect.delayMillis)
                    performStallRecovery(effect.attempt)
                }
            }
        }
    }

    /**
     * [attempt] picks light vs heavy via [StallRetryPolicy.recoveryKindFor] - light
     * (seekToDefaultPosition + prepare) jumps back to the live edge without tearing down decoders;
     * heavy (stop/prepare/play, the old unconditional behavior) is reserved for when two light
     * attempts in a row didn't help, since it costs a full re-buffer from zero.
     *
     * Both of [sampleForStall]'s preconditions are re-checked here, not only before the wait. This
     * runs up to thirty seconds after the stall that scheduled it (see [StallRetryPolicy]'s steady
     * state), which is long enough for either of them to have stopped being true - and heavy
     * recovery ends in `play()`, so acting on a stale one does not merely waste work, it starts
     * playback nobody asked for.
     *
     * `playWhenReady` false means the player is not trying to play any more: the user paused, the
     * app went to the background (see [onEnterBackground] and [BackgroundPlaybackPolicy]), or
     * ExoPlayer gave up audio focus for a call. Without this check the app resumed a stream the
     * user had just paused, and - after backgrounding - went on playing a live stream with the
     * screen off, which is the precise thing that pause existed to prevent.
     *
     * [isRemoteCasting] means the local player deliberately stood down for a receiver (see
     * [handleDlnaStateChange] and [LocalPlaybackPolicy]). `stop()` leaves `playWhenReady` true, so
     * the check above does not cover this one: recovering here would put the same stream on the
     * phone and the receiver at once - audible twice, with the phone taking the origin's one
     * allowed connection and starving the receiver that was playing fine.
     *
     * Skipping costs nothing. If the stream is still stalled once playback is genuinely wanted
     * again, the next [sampleForStall] tick schedules a fresh recovery.
     */
    @VisibleForTesting
    internal fun performStallRecovery(attempt: Int) {
        if (isRemoteCasting || !exoPlayer.playWhenReady) {
            AppLog.d(TAG) { "Skipping stall recovery: playback is not wanted right now" }
            cancelPendingStallRecovery()
            return
        }
        when (StallRetryPolicy.recoveryKindFor(attempt)) {
            StallRecoveryKind.LIGHT -> {
                exoPlayer.seekToDefaultPosition()
                exoPlayer.prepare()
            }
            StallRecoveryKind.HEAVY -> {
                exoPlayer.stop()
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    private fun updateSeekability() {
        val isLive = exoPlayer.isCurrentMediaItemLive
        val isSeekable = exoPlayer.isCurrentMediaItemSeekable
        // isRemoteCasting, not isCasting: the local player's own seekability says nothing about
        // what a receiver is doing, and this MVP sends no position commands to a DLNA renderer
        // either (see docs/DLNA.md), so the seek bar must be inert for both cast targets alike.
        _uiState.update { it.copy(canSeek = SeekPolicy.canSeek(isLive, isSeekable, isRemoteCasting)) }
    }

    private fun updateBadgesAndTrackLists(tracks: Tracks) {
        val snapshot = trackMapper.map(tracks)
        _uiState.update {
            it.copy(
                badges = snapshot.badges,
                audioTracks = snapshot.audioTracks,
                textTracks = snapshot.textTracks,
            )
        }
    }

    /** True only between a pause this app made because the screen went away and the resume that
     * undoes it - see [BackgroundPlaybackPolicy.shouldResumeOnStart]. */
    private var pausedForBackground = false

    /** Whether the app is off screen. Distinct from [pausedForBackground], which records only the
     * narrower fact that *this policy* was what paused: while casting nothing is paused at all, and
     * that is precisely when [resumeLocalPlayback] needs to know the phone is away. */
    private var isInBackground = false

    /** A local resume a cast handed back while the app was off screen, owed until it returns - see
     * [resumeLocalPlayback]. */
    private var resumeLocalWhenForeground = false

    /** A delayed error retry that became ready while the app was off screen. Kept separate from a
     * receiver handback because the events that create and cancel them are different, even though
     * both are paid by one fresh prepare/play on return. */
    private var retryLocalWhenForeground = false

    private fun performScheduledPlaybackRetry() {
        when (
            LocalPlaybackRetryPolicy.decide(
                isRemoteCasting = isRemoteCasting,
                isInBackground = isInBackground,
                wantsToPlay = exoPlayer.playWhenReady,
                pausedForBackground = pausedForBackground,
            )
        ) {
            LocalPlaybackRetryAction.PREPARE_NOW -> {
                retryLocalWhenForeground = false
                exoPlayer.prepare()
            }
            LocalPlaybackRetryAction.DEFER_UNTIL_FOREGROUND -> {
                AppLog.d(TAG) { "Playback retry became ready off screen - deferring until foreground" }
                retryLocalWhenForeground = true
            }
            LocalPlaybackRetryAction.DROP ->
                AppLog.d(TAG) { "Skipping stale playback retry: local playback is no longer wanted" }
        }
    }

    private fun cancelLocalRecoveryForRemotePlayback() {
        retryJob?.cancel()
        retryJob = null
        retryLocalWhenForeground = false
        cancelPendingStallRecovery()
    }

    private fun cancelPendingStallRecovery() {
        stallRecoveryJob?.cancel()
        stallRecoveryJob = null
        sessionStateMachine.cancelStallRecovery()
        if (_uiState.value.isRecoveringPlayback) {
            _uiState.update { it.copy(isRecoveringPlayback = false, stallRecoveryAttempt = 0) }
        }
    }

    /** The app is no longer on screen. See [BackgroundPlaybackPolicy] for what this is preventing. */
    fun onEnterBackground(isInPictureInPicture: Boolean) {
        // Picture-in-picture is still a window the user is looking at, so it does not count as off
        // screen for the purposes of starting playback - same distinction the pause decision below
        // draws, for the same reason.
        isInBackground = !isInPictureInPicture
        val shouldPause = BackgroundPlaybackPolicy.shouldPauseOnStop(
            // Both receivers own playback. Passing only the UI's Chromecast flag here made a
            // background/foreground round-trip call play() locally over an active DLNA session.
            isCasting = isRemoteCasting,
            // playWhenReady, not isPlaying - see the policy: a stream that is still buffering is not
            // "playing", and would otherwise have gone on downloading from a stopped activity.
            wantsToPlay = exoPlayer.playWhenReady,
            isInPictureInPicture = isInPictureInPicture,
        )
        if (!shouldPause) return
        pausedForBackground = true
        exoPlayer.pause()
    }

    /** The app is visible again. Resumes only what [onEnterBackground] stopped, so a channel the
     * user paused themselves stays paused. */
    fun onReturnToForeground() {
        isInBackground = false
        val shouldResume = BackgroundPlaybackPolicy.shouldResumeOnStart(
            pausedByPolicy = pausedForBackground,
            isCasting = isRemoteCasting,
        )
        // Cleared either way: the pause it records has been answered for, and carrying it forward
        // would resume a later, unrelated backgrounding that the user had paused through.
        pausedForBackground = false
        // A cast that ended off screen left local playback owed instead of started - see
        // resumeLocalPlayback. Cleared either way, for the same reason as above, and only paid if
        // nothing has taken the stream back in the meantime.
        val owedLocalResume = resumeLocalWhenForeground
        resumeLocalWhenForeground = false
        val owedLocalRetry = retryLocalWhenForeground
        retryLocalWhenForeground = false
        when {
            (owedLocalResume || owedLocalRetry) && !isRemoteCasting -> resumeLocalPlayback()
            shouldResume -> exoPlayer.play()
            else -> Unit
        }
    }

    /** Test seam for the lifecycle's two independent receiver flags. Production changes them only
     * through the repository collectors above. */
    @VisibleForTesting
    internal fun setRemoteCastingForLifecycleTest(chromecast: Boolean, dlna: Boolean) {
        isCasting = chromecast
        isDlnaCasting = dlna
        _uiState.update { it.copy(isCasting = chromecast) }
        PlaybackActivity.setActive(_uiState.value.isPlaying || isRemoteCasting)
    }

    /**
     * Frees the loaded stream and its decoder/buffer resources when the player is fully closed,
     * WITHOUT destroying this (Activity-scoped, reused-across-reopens) ViewModel - see
     * [com.uacastplayer.ui.player.PlayerHost]. stop() releases the codecs and buffered samples,
     * clearMediaItems() drops the stream, so an idle closed player costs almost nothing; the
     * ExoPlayer and MediaSession instances themselves are kept for the next open. [onCleared] (on
     * Activity destroy) is what releases the instances.
     */
    fun releasePlayback() {
        pausedForBackground = false
        // Nothing is owed to a player with no stream left in it.
        resumeLocalWhenForeground = false
        retryLocalWhenForeground = false
        navigation.cancelPendingSwitch()
        retryJob?.cancel()
        retryJob = null
        cancelPendingStallRecovery()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        sessionStateMachine.release()
        castArtworkUrlFor = DEFAULT_CAST_ARTWORK
        _uiState.update { previous ->
            PlayerUiState(
                isCasting = isCasting,
                castStatusMessage = previous.castStatusMessage.takeIf { isCasting },
                resizeMode = previous.resizeMode,
            )
        }
        // Closing the phone UI does not end an active Cast/DLNA session. Keep background icon
        // prefetch gated while the receiver is still consuming the same network connection.
        PlaybackActivity.setActive(isRemoteCasting)
    }

    override fun onCleared() {
        navigation.cancelPendingSwitch()
        retryJob?.cancel()
        retryJob = null
        cancelPendingStallRecovery()
        exoPlayer.removeListener(listener)
        mediaSession?.release()
        exoPlayer.release()
        liveInstances.decrementAndGet()
        PlaybackActivity.setActive(isRemoteCasting)
        super.onCleared()
    }

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val STALL_SAMPLE_INTERVAL_MILLIS = 2_000L

        /** What [start] falls back to when no resolver is supplied: the channel's own `tvg-logo`,
         * the only artwork source this class had before [start] took one. */
        private val DEFAULT_CAST_ARTWORK: (M3uChannel) -> String? = { it.tvgLogo }

        // Process-wide guard: at most one PlayerViewModel (hence one ExoPlayer) may be alive at a
        // time. Incremented as the first thing each instance does, decremented in onCleared; a value
        // greater than one means the double-ExoPlayer leak this whole fix exists to prevent has come
        // back (see PlayerInstanceGuard for the interpretation).
        private val liveInstances = AtomicInteger(0)

        /** Instrumentation-test hook for the invariant this whole guard exists to enforce: never
         * more than one live [PlayerViewModel] (see PlayerInstanceGuard.isLeak). */
        @VisibleForTesting
        fun liveInstanceCountForTest(): Int = liveInstances.get()
    }
}
