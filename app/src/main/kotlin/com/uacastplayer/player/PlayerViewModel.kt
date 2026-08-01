package com.uacastplayer.player

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.uacastplayer.BuildConfig
import com.uacastplayer.MainActivity
import com.uacastplayer.cast.CastSessionRepository
import com.uacastplayer.cast.CastSideEffect
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.data.prefs.BufferSize
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.dlna.DlnaDevice
import com.uacastplayer.dlna.DlnaSessionRepository
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.log.AppLog
import com.uacastplayer.R
import com.uacastplayer.playlist.M3uChannel
import java.util.Locale
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

    private val dataSourceFactory = PlayerDataSourceFactory.create(application)

    // Many real-world IPTV origins send TS streams with non-standard headers (non-IDR keyframes
    // marked as random-access points, PES packets that don't cleanly align to access units) that
    // the default TS extractor rejects outright, failing playback before it starts. These flags
    // only relax TS parsing tolerance - they don't touch the (separate) HLS extraction path.
    private val extractorsFactory = DefaultExtractorsFactory().setTsExtractorFlags(
        DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
            DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
    )

    // handleAudioFocus=true lets ExoPlayer request/abandon audio focus and duck/pause on its own
    // for calls and other apps' audio; setHandleAudioBecomingNoisy pauses when headphones/BT
    // disconnect so playback doesn't suddenly blast through the speaker. Both only ever touch this
    // local player - while casting, switchToIndexImmediate() keeps it stopped (see
    // LocalPlaybackPolicy) instead of playing, so it never holds focus and a focus loss elsewhere
    // has nothing local to pause; the cast receiver's playback state is owned entirely by
    // CastSessionRepository and is unreachable from this player's focus/noisy callbacks.
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(application, PlayerRenderersFactoryProvider.create(application))
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory))
        .setLoadControl(buildLoadControl(preferences.bufferSize))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    // Exposes this local player to system media surfaces (headset buttons, a watch, the system
    // media notification) - a full MediaSessionService with background playback is intentionally
    // out of scope here (this is a live-TV app; playback isn't expected to survive the player
    // screen closing), so this session just lives and dies alongside the ExoPlayer instance.
    //
    // A single FIXED id, deliberately not made unique per instance. This ViewModel is now
    // Activity-scoped and there is only ever one alive at a time (see PlayerHost and the
    // liveInstances guard above), so a fixed id is correct - and it doubles as a loud backstop:
    // Media3 rejects a second MediaSession sharing an id with one still alive, so if the
    // single-instance invariant ever breaks again, that breakage surfaces immediately instead of
    // quietly leaking an ExoPlayer. Session creation is still treated as a convenience, not a hard
    // dependency: if it fails for any reason the player keeps working without system media controls
    // rather than crashing the process over headset-button support.
    private val mediaSession: MediaSession? = try {
        MediaSession.Builder(application, exoPlayer)
            .setId(PLAYER_SESSION_ID)
            .setCallback(MediaSessionCallback())
            // Without an explicit session activity, Media3 falls back to building its own implicit
            // "open the app" intent to satisfy the system media notification/lock-screen controls -
            // on API 30+, an implicit intent with no explicit component often can't be resolved at
            // all (package-visibility restrictions), logging "Unresolvable implicit intent" and
            // leaving the session without a way to bring the app back to the foreground from those
            // system surfaces. An explicit PendingIntent to this app's own MainActivity always
            // resolves.
            .setSessionActivity(
                PendingIntent.getActivity(
                    application,
                    0,
                    Intent(application, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    } catch (e: IllegalStateException) {
        AppLog.w(TAG) { "MediaSession creation failed, continuing without system media controls: ${e.message}" }
        null
    }

    val player: Player get() = exoPlayer

    private var channels: List<M3uChannel> = emptyList()
    private var currentIndex: Int = -1
    private val deadIndices = mutableSetOf<Int>()
    private var retryState = RetryState()
    private var liveWindowRecoveryHistory: List<Long> = emptyList()
    private var stallState = StallDetectionPolicy.StallState.NONE
    private var stallRetryState = StallRetryPolicy.State()
    private var channelHistory = ChannelHistoryPolicy.State(current = null, previous = null)
    private var pendingSwitchJob: Job? = null
    private var retryJob: Job? = null
    private var stallRecoveryJob: Job? = null

    var wrapAroundEnabled: Boolean = preferences.wrapAroundEnabled
    var autoSkipDeadEnabled: Boolean = preferences.autoSkipDeadEnabled

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

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                retryState = PlaybackRetryPolicy.onIsPlaying(retryState)
                liveWindowRecoveryHistory = emptyList()
            }
            _uiState.update { it.copy(isPlaying = isPlaying) }
            PlaybackActivity.setActive(isPlaying || isRemoteCasting)
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
                        castCodecIncompatibility = state.codecIncompatibility,
                        castReceiverLoadFailed = state.receiverLoadFailed,
                        castLikelyCompatibilityHint = state.likelyCompatibilityHint,
                        castIsRecovering = state.isRecovering,
                        castProxyUnavailableIpv4Only = state.proxyUnavailableIpv4Only,
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
            exoPlayer.stop()
        } else {
            exoPlayer.prepare()
            exoPlayer.play()
        }
        updateSeekability()
        PlaybackActivity.setActive(_uiState.value.isPlaying || isRemoteCasting)
    }

    suspend fun discoverDlnaDevices(): List<DlnaDevice> = dlnaRepository.discoverDevices()

    /** No-op with no channel loaded - there would be nothing to hand the renderer. */
    fun connectDlna(device: DlnaDevice) {
        val channel = _uiState.value.currentChannel ?: return
        dlnaRepository.connect(device, channel.streamUrl, channel.displayName)
    }

    fun stopDlna() = dlnaRepository.stop()

    private fun handleCastSideEffect(effect: CastSideEffect) {
        when (effect) {
            // stop(), not pause(): a paused live stream keeps its upstream connection (and often
            // keeps buffering), and IPTV providers routinely enforce ONE connection per account -
            // as long as the phone holds that slot, the receiver's own fetch (direct mode) or the
            // proxy's remux reader can never get a working connection, so casting starves forever.
            // The media item survives stop(), and ResumeLocalPlayer's prepare()+play() below fully
            // recovers from it. sampleForStall can't fight this: it bails while isCasting.
            CastSideEffect.PauseLocalPlayer -> exoPlayer.stop()
            CastSideEffect.ResumeLocalPlayer -> {
                // While casting, switchToIndexImmediate() skips prepare() for whatever channel is
                // current (see there) so the phone isn't buffering the same stream twice in
                // parallel with the receiver - the media item is still set, just never prepared,
                // so it must be prepared here before play() can do anything.
                exoPlayer.prepare()
                exoPlayer.play()
            }
            is CastSideEffect.ApplyPendingChannelSwitch -> switchToIndexImmediate(effect.index)
            is CastSideEffect.RecordIncompatibility ->
                AppLog.d(TAG) { "Cast incompatibility recorded: ${effect.reason}" }
            CastSideEffect.CloseProxySession -> Unit // CastSessionRepository owns and closes the proxy itself.
        }
    }

    fun start(channels: List<M3uChannel>, startIndex: Int) {
        this.channels = channels
        deadIndices.clear()
        retryState = RetryState()
        liveWindowRecoveryHistory = emptyList()
        if (channels.isNotEmpty()) switchToIndexImmediate(startIndex.coerceIn(channels.indices))
    }

    fun requestSwitch(index: Int) {
        if (index !in channels.indices) return
        pendingSwitchJob?.cancel()
        pendingSwitchJob = viewModelScope.launch {
            delay(CHANNEL_SWITCH_DEBOUNCE_MILLIS)
            switchToIndexImmediate(index)
        }
    }

    fun requestNext() {
        val next = ChannelNavigator.nextIndex(currentIndex, channels.size, wrapAroundEnabled) ?: return
        requestSwitch(next)
    }

    fun requestPrevious() {
        val previous = ChannelNavigator.previousIndex(currentIndex, channels.size, wrapAroundEnabled) ?: return
        requestSwitch(previous)
    }

    /** Jumps back to whatever channel was current right before the last switch - like a TV
     * remote's last-channel button, not list-adjacency (see [requestPrevious] for that). Pressing
     * it again jumps right back, since [ChannelHistoryPolicy.onSwitch] swaps current/previous on
     * every switch to a genuinely different channel. */
    fun requestPreviousChannel() {
        val previous = channelHistory.previous ?: return
        requestSwitch(previous)
    }

    fun seekTo(positionMs: Long) {
        if (!_uiState.value.canSeek) return
        exoPlayer.seekTo(positionMs)
    }

    /** Global, not per-channel - see [com.uacastplayer.data.prefs.PlayerResizeMode]. A transient
     * on-screen label is the UI's job (see [PlayerUiState.resizeMode] + [ResizeModeCycle.labelRes]),
     * this only owns the persisted value. */
    fun cycleResizeMode() {
        val next = ResizeModeCycle.next(_uiState.value.resizeMode)
        preferences.playerResizeMode = next
        _uiState.update { it.copy(resizeMode = next) }
    }

    fun selectAudioTrack(track: SelectableTrack) = selectTrack(track, C.TRACK_TYPE_AUDIO)

    fun selectTextTrack(track: SelectableTrack) = selectTrack(track, C.TRACK_TYPE_TEXT)

    fun clearTextTrack() {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private fun selectTrack(track: SelectableTrack, trackType: Int) {
        val override = TrackSelectionOverride(track.trackGroup, track.indexInGroup)
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(trackType, false)
            .setOverrideForType(override)
            .build()
    }

    private fun switchToIndexImmediate(index: Int) {
        if (index !in channels.indices) return
        channelHistory = ChannelHistoryPolicy.onSwitch(channelHistory, index)
        currentIndex = index
        stallState = StallDetectionPolicy.StallState.NONE
        stallRetryState = StallRetryPolicy.State()
        stallRecoveryJob?.cancel()
        val channel = channels[index]
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
        castRepository.setActiveChannel(index, channel.streamUrl, channel.displayName, channel.userAgent, channel.referrer)
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
                nextChannelsPreview = buildPreview(index),
                fatalError = false,
                hasPreviousChannel = channelHistory.previous != null,
                isRecoveringPlayback = false,
                stallRecoveryAttempt = 0,
            )
        }
    }

    private fun buildPreview(fromIndex: Int): List<IndexedChannel> {
        if (channels.size <= 1) return emptyList()
        val preview = mutableListOf<IndexedChannel>()
        var index = fromIndex
        repeat(minOf(MAX_PREVIEW_SIZE, channels.size - 1)) {
            val next = ChannelNavigator.nextIndex(index, channels.size, wrapAroundEnabled) ?: return preview
            preview += IndexedChannel(next, channels[next])
            index = next
        }
        return preview
    }

    private fun handlePlaybackError(error: PlaybackException) {
        val errorType = PlayerErrorClassifier.classify(error)
        if (errorType == PlaybackErrorType.BEHIND_LIVE_WINDOW && recoverFromBehindLiveWindow()) return
        when (val decision = PlaybackRetryPolicy.onError(retryState, errorType)) {
            is RetryDecision.Retry -> {
                retryState = decision.newState
                retryJob?.cancel()
                retryJob = viewModelScope.launch {
                    delay(decision.delayMillis)
                    exoPlayer.prepare()
                }
            }
            RetryDecision.GiveUp -> giveUpOnCurrentChannel()
        }
    }

    private fun giveUpOnCurrentChannel() {
        deadIndices += currentIndex
        val next = if (autoSkipDeadEnabled) {
            ChannelNavigator.nextPlayableIndex(currentIndex, channels.size, wrapAroundEnabled) {
                it in deadIndices
            }
        } else {
            null
        }
        if (next != null) {
            switchToIndexImmediate(next)
        } else {
            _uiState.update { it.copy(fatalError = true, isBuffering = false) }
        }
    }

    /**
     * A long live view inevitably outruns HLS's live window eventually - this is the canonical
     * ExoPlayer-documented fix (seek back into the live window, then re-prepare), applied silently
     * rather than surfacing it as a playback error. Returns true if it handled the error (caller
     * should stop here); false means [LiveWindowRecoveryPolicy] gave up and the normal error path
     * (see [PlaybackRetryPolicy]) should run instead - this stream's live window is shrinking faster
     * than playback can keep up with, which a seek can't fix.
     */
    private fun recoverFromBehindLiveWindow(): Boolean {
        val decision = LiveWindowRecoveryPolicy.onBehindLiveWindow(
            System.currentTimeMillis(),
            liveWindowRecoveryHistory,
        )
        if (decision !is LiveWindowRecoveryPolicy.Decision.Recover) return false
        liveWindowRecoveryHistory = decision.newHistory
        AppLog.d(TAG) {
            "Recovering from BehindLiveWindowException (attempt ${decision.newHistory.size} in the last 60s)"
        }
        exoPlayer.seekToDefaultPosition()
        exoPlayer.prepare()
        return true
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
        if (isRemoteCasting || currentIndex !in channels.indices || !exoPlayer.playWhenReady) return

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
        val threshold = StallDetectionPolicy.thresholdMillisFor(preferences.bufferSize)
        val result = StallDetectionPolicy.evaluate(tick, stallState, threshold)
        stallState = result.state
        if (result.health != StallDetectionPolicy.Health.STALLED) {
            // Only a genuinely healthy (not merely in-grace) tick clears the "recovering" UI - see
            // Result.inGracePeriod's KDoc.
            if (!result.inGracePeriod && _uiState.value.isRecoveringPlayback) {
                _uiState.update { it.copy(isRecoveringPlayback = false, stallRecoveryAttempt = 0) }
            }
            return
        }

        val decision = StallRetryPolicy.onStall(tick.nowMillis, stallRetryState)
        stallRetryState = decision.newState
        // Armed immediately (not after the delay) so ticks during the wait - which will see the
        // still-stalled player - don't pile up a second, overlapping stall streak.
        stallState = StallDetectionPolicy.afterRecovery(tick.nowMillis, threshold)
        _uiState.update { it.copy(isRecoveringPlayback = true, stallRecoveryAttempt = decision.newState.attempt) }
        AppLog.d(TAG) {
            "Recovering from a silent stall: attempt ${decision.newState.attempt}," +
                " retrying in ${decision.delayMillis}ms"
        }

        stallRecoveryJob?.cancel()
        stallRecoveryJob = viewModelScope.launch {
            delay(decision.delayMillis)
            performStallRecovery(decision.newState.attempt)
        }
    }

    /** [attempt] picks light vs heavy via [StallRetryPolicy.recoveryKindFor] - light
     * (seekToDefaultPosition + prepare) jumps back to the live edge without tearing down decoders;
     * heavy (stop/prepare/play, the old unconditional behavior) is reserved for when two light
     * attempts in a row didn't help, since it costs a full re-buffer from zero. */
    private fun performStallRecovery(attempt: Int) {
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

    private data class SelectedVideoFormat(
        val width: Int?,
        val height: Int,
        val mime: String?,
        val frameRate: Float?,
        val bitrateBps: Int?,
    )

    private data class SelectedAudioFormat(
        val mime: String?,
        val channelCount: Int?,
        val sampleRateHz: Int?,
        val bitrateBps: Int?,
    )

    private fun updateBadgesAndTrackLists(tracks: Tracks) {
        var selectedVideo: SelectedVideoFormat? = null
        var selectedAudio: SelectedAudioFormat? = null
        val audioTracks = mutableListOf<SelectableTrack>()
        val textTracks = mutableListOf<SelectableTrack>()

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) selectedVideo = selectedVideoFormat(group.getTrackFormat(i))
                }
                C.TRACK_TYPE_AUDIO -> for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val selected = group.isTrackSelected(i)
                    audioTracks += audioSelectableTrack(group, i, format, selected)
                    if (selected) selectedAudio = selectedAudioFormat(format)
                }
                C.TRACK_TYPE_TEXT -> for (i in 0 until group.length) {
                    textTracks += textSelectableTrack(group, i)
                }
                else -> Unit
            }
        }

        _uiState.update {
            it.copy(
                badges = badgesState(selectedVideo, selectedAudio),
                audioTracks = audioTracks,
                textTracks = textTracks,
            )
        }
    }

    private fun selectedVideoFormat(format: Format) = SelectedVideoFormat(
        width = format.width.takeIf { it > 0 },
        height = format.height,
        mime = format.sampleMimeType,
        frameRate = format.frameRate.takeIf { it > 0f && it != Format.NO_VALUE.toFloat() },
        bitrateBps = format.bitrate.takeIf { it != Format.NO_VALUE },
    )

    private fun selectedAudioFormat(format: Format) = SelectedAudioFormat(
        mime = format.sampleMimeType,
        channelCount = format.channelCount.takeIf { it != Format.NO_VALUE },
        sampleRateHz = format.sampleRate.takeIf { it != Format.NO_VALUE },
        bitrateBps = format.bitrate.takeIf { it != Format.NO_VALUE },
    )

    private fun audioSelectableTrack(group: Tracks.Group, index: Int, format: Format, selected: Boolean): SelectableTrack {
        val channelCount = format.channelCount.takeIf { it != Format.NO_VALUE }
        return SelectableTrack(
            trackGroup = group.mediaTrackGroup,
            indexInGroup = index,
            label = trackLabel(format.language, format.label, index + 1),
            isSelected = selected,
            codecLabel = PlaybackBadges.audioCodecLabel(format.sampleMimeType),
            channelLayout = channelCount?.let(PlaybackBadges::channelLayout),
            sampleRateHz = format.sampleRate.takeIf { it != Format.NO_VALUE },
            bitrateBps = format.bitrate.takeIf { it != Format.NO_VALUE },
        )
    }

    private fun textSelectableTrack(group: Tracks.Group, index: Int): SelectableTrack {
        val format = group.getTrackFormat(index)
        return SelectableTrack(
            trackGroup = group.mediaTrackGroup,
            indexInGroup = index,
            label = trackLabel(format.language, format.label, index + 1),
            isSelected = group.isTrackSelected(index),
            codecLabel = PlaybackBadges.textCodecLabel(format.sampleMimeType),
        )
    }

    private fun badgesState(video: SelectedVideoFormat?, audio: SelectedAudioFormat?) = PlaybackBadgesState(
        qualityLabel = video?.height?.let(PlaybackBadges::qualityLabel),
        videoCodecLabel = PlaybackBadges.videoCodecLabel(video?.mime),
        audioCodecLabel = PlaybackBadges.audioCodecLabel(audio?.mime),
        channelLayout = audio?.channelCount?.let(PlaybackBadges::channelLayout),
        videoWidth = video?.width,
        videoHeight = video?.height?.takeIf { it > 0 },
        frameRate = video?.frameRate,
        videoBitrateBps = video?.bitrateBps,
        audioBitrateBps = audio?.bitrateBps,
        audioSampleRateHz = audio?.sampleRateHz,
    )

    private fun trackLabel(language: String?, label: String?, index: Int): String {
        if (!label.isNullOrBlank()) return label
        if (!language.isNullOrBlank()) {
            return try {
                Locale.Builder().setLanguage(language).build().displayLanguage.ifBlank { language }
            } catch (_: Exception) {
                language
            }
        }
        return getApplication<Application>().getString(R.string.player_track_unknown, index)
    }

    /**
     * Frees the loaded stream and its decoder/buffer resources when the player is fully closed,
     * WITHOUT destroying this (Activity-scoped, reused-across-reopens) ViewModel - see [PlayerHost].
     * stop() releases the codecs and buffered samples, clearMediaItems() drops the stream, so an
     * idle closed player costs almost nothing; the ExoPlayer and MediaSession instances themselves
     * are kept for the next open. [onCleared] (on Activity destroy) is what releases the instances.
     */
    fun releasePlayback() {
        pendingSwitchJob?.cancel()
        retryJob?.cancel()
        stallRecoveryJob?.cancel()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        currentIndex = -1
        channels = emptyList()
        _uiState.update { PlayerUiState(resizeMode = it.resizeMode) }
        PlaybackActivity.setActive(false)
    }

    override fun onCleared() {
        pendingSwitchJob?.cancel()
        retryJob?.cancel()
        stallRecoveryJob?.cancel()
        exoPlayer.removeListener(listener)
        mediaSession?.release()
        exoPlayer.release()
        liveInstances.decrementAndGet()
        PlaybackActivity.setActive(false)
        super.onCleared()
    }

    /**
     * ExoPlayer's built-in next/previous-media-item commands are no-ops with only one MediaItem
     * loaded at a time, so a channel list isn't visible to connected controllers by default. This
     * force-enables those two commands for the session and redirects them to the same channel
     * switch the on-screen next/previous buttons use - see [MediaSessionCommandPolicy].
     */
    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availablePlayerCommands = connectionResult.availablePlayerCommands.buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
            return MediaSession.ConnectionResult.accept(connectionResult.availableSessionCommands, availablePlayerCommands)
        }

        /**
         * Deprecated in media3, which now points at wrapping the player in a `ForwardingPlayer`
         * that overrides `seekToNext`/`seekToPreviousMediaItem` and friends. Deliberately NOT
         * migrated yet, and the reason is worth writing down rather than rediscovering:
         *
         * a `ForwardingPlayer` has to keep `getAvailableCommands()` and `isCommandAvailable()`
         * agreeing with each other, and the only surfaces that exercise any of this are system
         * ones - a headset, a watch, the media notification. None of them are reachable from a
         * unit test, so a mistake would show up as next/previous silently doing nothing on a
         * user's notification, discovered weeks later. The deprecated callback here works, is
         * still called by media3 1.10.1, and is a handful of lines; trading it for an untestable
         * refactor is not an improvement.
         *
         * Do the migration when there is a device to check it on, together with
         * [MediaSessionCommandPolicy], which already isolates the mapping the new player would
         * need.
         */
        @Suppress("DEPRECATION")
        @Deprecated("Kept until the ForwardingPlayer migration can be verified on a device.")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            @Player.Command playerCommand: Int,
        ): Int {
            val action = MediaSessionCommandPolicy.mapCommand(playerCommand)
                ?: return super.onPlayerCommandRequest(session, controller, playerCommand)
            when (action) {
                MediaSessionCommandPolicy.Action.NEXT -> requestNext()
                MediaSessionCommandPolicy.Action.PREVIOUS -> requestPrevious()
            }
            return SessionResult.RESULT_ERROR_NOT_SUPPORTED
        }
    }

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val CHANNEL_SWITCH_DEBOUNCE_MILLIS = 220L
        private const val MAX_PREVIEW_SIZE = 20
        private const val STALL_SAMPLE_INTERVAL_MILLIS = 2_000L

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

/**
 * SMALL favors fast channel switching/startup on stable connections at the risk of more
 * rebuffering; LARGE trades that latency for resilience on slow or unstable networks. MEDIUM's
 * min/max match [DefaultLoadControl]'s own defaults (cruise-time resilience is unchanged), but its
 * bufferForPlayback* values are their own tier below that - the *startup* threshold (how much must
 * be buffered before playback begins, or resumes after a rebuffer) is a completely different
 * tradeoff from the *cruise* buffer (how much this player is willing to hold once playing, to ride
 * out network hiccups without rebuffering) and always waiting for DefaultLoadControl's full 2.5s
 * default before ever starting a channel is the single biggest contributor to channel-switch
 * latency. LARGE keeps that same split, just at correspondingly higher absolute values for a
 * genuinely unstable connection.
 */
private const val BYTES_PER_MB = 1024 * 1024
private const val SMALL_TARGET_BYTES = 8 * BYTES_PER_MB
private const val MEDIUM_TARGET_BYTES = 16 * BYTES_PER_MB
private const val LARGE_TARGET_BYTES = 24 * BYTES_PER_MB

// 90s of cruise buffer was VOD-sized overkill for a live stream (there is no seeking backward to
// justify holding that much) and, on a high-bitrate channel, the single biggest driver of heap
// pressure. 35s still rides out ordinary hiccups on an unstable link; the byte cap is the real
// backstop regardless of bitrate.
private const val LARGE_MAX_BUFFER_MS = 35_000

@UnstableApi
private fun buildLoadControl(bufferSize: BufferSize): DefaultLoadControl {
    val profile = when (bufferSize) {
        BufferSize.SMALL -> BufferProfile(10_000, 20_000, 1_000, 2_000, SMALL_TARGET_BYTES)
        BufferSize.MEDIUM -> BufferProfile(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            1_000,
            2_500,
            MEDIUM_TARGET_BYTES,
        )
        BufferSize.LARGE -> BufferProfile(30_000, LARGE_MAX_BUFFER_MS, 2_000, 5_000, LARGE_TARGET_BYTES)
    }
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            profile.minBufferMs,
            profile.maxBufferMs,
            profile.bufferForPlaybackMs,
            profile.bufferForPlaybackAfterRebufferMs,
        )
        // Explicit hard cap in BYTES, independent of the ms thresholds above. DefaultLoadControl's
        // default byte target is derived from duration and can grow very large on high-bitrate
        // streams - the exact path to OutOfMemoryError. This bounds one player's held media to a
        // fixed ceiling no matter the bitrate, which is the primary OOM safeguard.
        .setTargetBufferBytes(profile.targetBufferBytes)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}

private data class BufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val targetBufferBytes: Int,
)
