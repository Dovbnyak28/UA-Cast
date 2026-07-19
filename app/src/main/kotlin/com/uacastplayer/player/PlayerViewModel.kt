package com.uacastplayer.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import com.uacastplayer.cast.CastSessionRepository
import com.uacastplayer.cast.CastSideEffect
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.data.prefs.BufferSize
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.log.AppLog
import com.uacastplayer.R
import com.uacastplayer.playlist.M3uChannel
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the single ExoPlayer instance for a player session. Scoped to the player's nested
 * NavHost destination so the instance (and this view model) is released the moment the user
 * backs out of the player, rather than living for the whole app process.
 */
@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

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
    private val mediaSession: MediaSession = MediaSession.Builder(application, exoPlayer)
        .setCallback(MediaSessionCallback())
        .build()

    val player: Player get() = exoPlayer

    private var channels: List<M3uChannel> = emptyList()
    private var currentIndex: Int = -1
    private val deadIndices = mutableSetOf<Int>()
    private var retryState = RetryState()
    private var liveWindowRecoveryHistory: List<Long> = emptyList()
    private var stallState = StallDetectionPolicy.StallState.NONE
    private var lastStallRecoveryAtMillis: Long? = null
    private var channelHistory = ChannelHistoryPolicy.State(current = null, previous = null)
    private var pendingSwitchJob: Job? = null
    private var retryJob: Job? = null

    var wrapAroundEnabled: Boolean = preferences.wrapAroundEnabled
    var autoSkipDeadEnabled: Boolean = preferences.autoSkipDeadEnabled

    private val castRepository = CastSessionRepository.getInstance(application)
    private var isCasting: Boolean = false

    private val _uiState = MutableStateFlow(PlayerUiState(resizeMode = preferences.playerResizeMode))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                retryState = PlaybackRetryPolicy.onIsPlaying(retryState)
                liveWindowRecoveryHistory = emptyList()
            }
            _uiState.update { it.copy(isPlaying = isPlaying) }
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
        exoPlayer.addListener(listener)
        exoPlayer.playWhenReady = true

        viewModelScope.launch {
            castRepository.state.collect { state ->
                isCasting = state.isSessionConnected
                _uiState.update {
                    it.copy(isCasting = isCasting, castCodecIncompatibility = state.codecIncompatibility)
                }
                updateSeekability()
            }
        }
        viewModelScope.launch {
            castRepository.sideEffects.collect { effect -> handleCastSideEffect(effect) }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(STALL_SAMPLE_INTERVAL_MILLIS)
                sampleForStall()
            }
        }
    }

    private fun handleCastSideEffect(effect: CastSideEffect) {
        when (effect) {
            CastSideEffect.PauseLocalPlayer -> exoPlayer.pause()
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
        val channel = channels[index]
        preferences.lastWatchedChannelKey = FavoriteKey.of(channel)
        dataSourceFactory.setChannelHeaders(channel.userAgent, channel.referrer)
        exoPlayer.setMediaItem(MediaItemFactory.forChannel(channel.streamUrl))
        if (LocalPlaybackPolicy.shouldPrepareLocally(isCasting)) {
            exoPlayer.prepare()
        } else {
            // The media item is still set so ResumeLocalPlayer (on cast disconnect) can prepare
            // it fresh once local playback resumes - just not prepared right now.
            exoPlayer.stop()
        }
        castRepository.setActiveChannel(index, channel.streamUrl, channel.displayName, channel.userAgent, channel.referrer)
        _uiState.update {
            it.copy(
                currentChannel = channel,
                isBuffering = true,
                badges = PlaybackBadgesState(),
                nextChannelsPreview = buildPreview(index),
                fatalError = false,
                hasPreviousChannel = channelHistory.previous != null,
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
     */
    private fun sampleForStall() {
        if (isCasting || currentIndex !in channels.indices || !exoPlayer.playWhenReady) return

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
        if (result.health != StallDetectionPolicy.Health.STALLED) return
        stallState = StallDetectionPolicy.StallState.NONE

        val lastRecovery = lastStallRecoveryAtMillis
        val withinCooldown = lastRecovery != null && tick.nowMillis - lastRecovery < STALL_RECOVERY_COOLDOWN_MILLIS
        if (withinCooldown) {
            // Recovered less than 30s ago and already stalled again - a silent stop/prepare/play
            // isn't fixing this stream, so stop pretending it will.
            AppLog.d(TAG) { "Stall recovery cooldown active - giving up on this channel" }
            giveUpOnCurrentChannel()
        } else {
            lastStallRecoveryAtMillis = tick.nowMillis
            AppLog.d(TAG) { "Recovering from a silent stall (stop/prepare/play, channel preserved)" }
            exoPlayer.stop()
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    private fun updateSeekability() {
        val isLive = exoPlayer.isCurrentMediaItemLive
        val isSeekable = exoPlayer.isCurrentMediaItemSeekable
        _uiState.update { it.copy(canSeek = SeekPolicy.canSeek(isLive, isSeekable, isCasting)) }
    }

    private fun updateBadgesAndTrackLists(tracks: Tracks) {
        var videoHeight = 0
        var videoMime: String? = null
        var audioMime: String? = null
        var audioChannelCount: Int? = null
        val audioTracks = mutableListOf<SelectableTrack>()
        val textTracks = mutableListOf<SelectableTrack>()

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) {
                        val format = group.getTrackFormat(i)
                        videoHeight = format.height
                        videoMime = format.sampleMimeType
                    }
                }

                C.TRACK_TYPE_AUDIO -> for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val selected = group.isTrackSelected(i)
                    if (selected) {
                        audioMime = format.sampleMimeType
                        audioChannelCount = format.channelCount.takeIf { it != androidx.media3.common.Format.NO_VALUE }
                    }
                    audioTracks += SelectableTrack(
                        trackGroup = group.mediaTrackGroup,
                        indexInGroup = i,
                        label = trackLabel(format.language, format.label, i + 1),
                        isSelected = selected,
                    )
                }

                C.TRACK_TYPE_TEXT -> for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    textTracks += SelectableTrack(
                        trackGroup = group.mediaTrackGroup,
                        indexInGroup = i,
                        label = trackLabel(format.language, format.label, i + 1),
                        isSelected = group.isTrackSelected(i),
                    )
                }

                else -> Unit
            }
        }

        _uiState.update {
            it.copy(
                badges = PlaybackBadgesState(
                    qualityLabel = PlaybackBadges.qualityLabel(videoHeight),
                    videoCodecLabel = PlaybackBadges.videoCodecLabel(videoMime),
                    audioCodecLabel = PlaybackBadges.audioCodecLabel(audioMime),
                    channelLayout = audioChannelCount?.let(PlaybackBadges::channelLayout),
                ),
                audioTracks = audioTracks,
                textTracks = textTracks,
            )
        }
    }

    private fun trackLabel(language: String?, label: String?, index: Int): String {
        if (!label.isNullOrBlank()) return label
        if (!language.isNullOrBlank()) return Locale.Builder().setLanguage(language).build().displayLanguage
        return getApplication<Application>().getString(R.string.player_track_unknown, index)
    }

    override fun onCleared() {
        pendingSwitchJob?.cancel()
        retryJob?.cancel()
        exoPlayer.removeListener(listener)
        mediaSession.release()
        exoPlayer.release()
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

    private companion object {
        const val TAG = "PlayerViewModel"
        const val CHANNEL_SWITCH_DEBOUNCE_MILLIS = 220L
        const val MAX_PREVIEW_SIZE = 20
        const val STALL_SAMPLE_INTERVAL_MILLIS = 2_000L
        const val STALL_RECOVERY_COOLDOWN_MILLIS = 30_000L
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
@UnstableApi
private fun buildLoadControl(bufferSize: BufferSize): DefaultLoadControl {
    val (minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs) = when (bufferSize) {
        BufferSize.SMALL -> BufferProfile(10_000, 20_000, 1_000, 2_000)
        BufferSize.MEDIUM -> BufferProfile(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            1_000,
            2_500,
        )
        BufferSize.LARGE -> BufferProfile(30_000, 90_000, 2_000, 5_000)
    }
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}

private data class BufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
)
