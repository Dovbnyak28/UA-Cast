package com.uacastplayer.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.uacastplayer.cast.CastSessionRepository
import com.uacastplayer.cast.CastSideEffect
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.dlna.DlnaDevice
import com.uacastplayer.dlna.DlnaSessionRepository
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.M3uChannel
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the single ExoPlayer instance for a player session. Scoped to the player's nested
 * NavHost destination so the instance (and this view model) is released the moment the user
 * backs out of the player, rather than living for the whole app process.
 */
@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(application, PlayerRenderersFactoryProvider.create(application))
        .setMediaSourceFactory(DefaultMediaSourceFactory(PlayerDataSourceFactory.create(application)))
        .build()

    val player: Player get() = exoPlayer

    private var channels: List<M3uChannel> = emptyList()
    private var currentIndex: Int = -1
    private val deadIndices = mutableSetOf<Int>()
    private var retryState = RetryState()
    private var pendingSwitchJob: Job? = null
    private var retryJob: Job? = null

    private val preferences = AppPreferences(application)
    var wrapAroundEnabled: Boolean = preferences.wrapAroundEnabled
    var autoSkipDeadEnabled: Boolean = preferences.autoSkipDeadEnabled

    private val castRepository = CastSessionRepository.getInstance(application)
    private var isCasting: Boolean = false

    private val dlnaRepository = DlnaSessionRepository.getInstance(application)
    private var isDlnaCasting: Boolean = false
    val dlnaState: StateFlow<DlnaConnectionState> = dlnaRepository.state

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) retryState = PlaybackRetryPolicy.onIsPlaying(retryState)
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
                updateSeekability()
            }
        }
        viewModelScope.launch {
            castRepository.sideEffects.collect { effect -> handleCastSideEffect(effect) }
        }
        viewModelScope.launch {
            dlnaRepository.state.collect { state -> handleDlnaStateChange(state) }
        }
    }

    /**
     * DLNA has no receiver-status callbacks to drive a reducer off (unlike Cast's [CastSideEffect]
     * pipeline - see `docs/DLNA.md`), so local playback is paused/resumed directly off the
     * connected-device transition instead.
     */
    private fun handleDlnaStateChange(state: DlnaConnectionState) {
        val isConnected = state.connectedDevice != null
        if (isConnected && !isDlnaCasting) exoPlayer.pause()
        if (!isConnected && isDlnaCasting) exoPlayer.play()
        isDlnaCasting = isConnected
        updateSeekability()
    }

    suspend fun discoverDlnaDevices() = dlnaRepository.discoverDevices()

    fun connectDlna(device: DlnaDevice) {
        val channel = uiState.value.currentChannel ?: return
        dlnaRepository.connect(device, channel.streamUrl, channel.displayName)
    }

    fun stopDlna() {
        dlnaRepository.stop()
    }

    private fun handleCastSideEffect(effect: CastSideEffect) {
        when (effect) {
            CastSideEffect.PauseLocalPlayer -> exoPlayer.pause()
            CastSideEffect.ResumeLocalPlayer -> exoPlayer.play()
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

    fun seekTo(positionMs: Long) {
        if (!_uiState.value.canSeek) return
        exoPlayer.seekTo(positionMs)
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
        currentIndex = index
        val channel = channels[index]
        exoPlayer.setMediaItem(MediaItemFactory.forChannel(channel.streamUrl))
        exoPlayer.prepare()
        castRepository.setActiveChannel(index, channel.streamUrl, channel.displayName)
        _uiState.update {
            it.copy(
                currentChannel = channel,
                isBuffering = true,
                badges = PlaybackBadgesState(),
                nextChannelsPreview = buildPreview(index),
                fatalError = false,
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
        when (val decision = PlaybackRetryPolicy.onError(retryState, errorType)) {
            is RetryDecision.Retry -> {
                retryState = decision.newState
                retryJob?.cancel()
                retryJob = viewModelScope.launch {
                    delay(decision.delayMillis)
                    exoPlayer.prepare()
                }
            }
            RetryDecision.GiveUp -> {
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
        }
    }

    private fun updateSeekability() {
        val isLive = exoPlayer.isCurrentMediaItemLive
        val isSeekable = exoPlayer.isCurrentMediaItemSeekable
        _uiState.update { it.copy(canSeek = SeekPolicy.canSeek(isLive, isSeekable, isCasting || isDlnaCasting)) }
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
                        label = trackLabel(format.language, format.label),
                        isSelected = selected,
                    )
                }

                C.TRACK_TYPE_TEXT -> for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    textTracks += SelectableTrack(
                        trackGroup = group.mediaTrackGroup,
                        indexInGroup = i,
                        label = trackLabel(format.language, format.label),
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

    private fun trackLabel(language: String?, label: String?): String {
        if (!label.isNullOrBlank()) return label
        if (!language.isNullOrBlank()) return Locale.Builder().setLanguage(language).build().displayLanguage
        return "?"
    }

    override fun onCleared() {
        pendingSwitchJob?.cancel()
        retryJob?.cancel()
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }

    private companion object {
        const val TAG = "PlayerViewModel"
        const val CHANNEL_SWITCH_DEBOUNCE_MILLIS = 220L
        const val MAX_PREVIEW_SIZE = 20
    }
}
