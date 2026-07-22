package com.uacastplayer.player

import androidx.media3.common.TrackGroup
import com.uacastplayer.cast.CastCompatibilityVerdict
import com.uacastplayer.cast.CodecIncompatibility
import com.uacastplayer.data.prefs.PlayerResizeMode
import com.uacastplayer.playlist.M3uChannel

data class IndexedChannel(val index: Int, val channel: M3uChannel)

data class PlaybackBadgesState(
    val qualityLabel: String? = null,
    val videoCodecLabel: String? = null,
    val audioCodecLabel: String? = null,
    val channelLayout: AudioChannelLayout? = null,
    /** Exact decoded pixel dimensions, as opposed to [qualityLabel]'s bucketed "480p"/"1080p" -
     * for the quality dialog, which has room to show the real number instead of a bucket. */
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val frameRate: Float? = null,
    val videoBitrateBps: Int? = null,
    val audioBitrateBps: Int? = null,
    val audioSampleRateHz: Int? = null,
)

data class SelectableTrack(
    val trackGroup: TrackGroup,
    val indexInGroup: Int,
    val label: String,
    val isSelected: Boolean,
    /** Everything below is optional, measured-only (never guessed) extra detail for the track
     * picker dialogs - see PlayerScreen's TrackPickerDialog. Null fields are simply omitted from
     * the detail line instead of showing a placeholder. */
    val codecLabel: String? = null,
    val channelLayout: AudioChannelLayout? = null,
    val sampleRateHz: Int? = null,
    val bitrateBps: Int? = null,
)

data class PlayerUiState(
    val currentChannel: M3uChannel? = null,
    val isBuffering: Boolean = true,
    val isPlaying: Boolean = false,
    val badges: PlaybackBadgesState = PlaybackBadgesState(),
    val nextChannelsPreview: List<IndexedChannel> = emptyList(),
    val audioTracks: List<SelectableTrack> = emptyList(),
    val textTracks: List<SelectableTrack> = emptyList(),
    val canSeek: Boolean = false,
    val fatalError: Boolean = false,
    /** Mirrors the view model's private isCasting flag so the UI can decide things like
     * keepScreenOn - the phone screen doesn't need to stay awake for a receiver it isn't driving. */
    val isCasting: Boolean = false,
    /** Mirrors [com.uacastplayer.cast.CastPlaybackState.codecIncompatibility] - see
     * [com.uacastplayer.cast.CastCompatibilityPolicy] for how it's decided. */
    val castCodecIncompatibility: CodecIncompatibility? = null,
    /** Mirrors [com.uacastplayer.cast.CastPlaybackState.receiverLoadFailed]. */
    val castReceiverLoadFailed: Boolean = false,
    /** Mirrors [com.uacastplayer.cast.CastPlaybackState.likelyCompatibilityHint] - only consulted
     * when [castReceiverLoadFailed] is true, to name a likely cause instead of a generic message. */
    val castLikelyCompatibilityHint: CastCompatibilityVerdict.LikelyCompatible? = null,
    /** Mirrors [com.uacastplayer.cast.CastPlaybackState.isRecovering]. */
    val castIsRecovering: Boolean = false,
    /** Mirrors [com.uacastplayer.cast.CastPlaybackState.proxyUnavailableIpv4Only]. */
    val castProxyUnavailableIpv4Only: Boolean = false,
    val resizeMode: PlayerResizeMode = PlayerResizeMode.DEFAULT,
    /** Whether [PlayerViewModel.requestPreviousChannel] has anywhere to go - false until a second
     * distinct channel has ever loaded this session. */
    val hasPreviousChannel: Boolean = false,
)
