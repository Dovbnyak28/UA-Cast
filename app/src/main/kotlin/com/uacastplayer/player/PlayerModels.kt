package com.uacastplayer.player

import androidx.media3.common.TrackGroup
import androidx.media3.common.VideoSize
import com.uacastplayer.cast.CastCompatibilityVerdict
import com.uacastplayer.cast.CastStatusMessage
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
    /** The decoded video's dimensions *and* pixel aspect ratio, straight from
     * [androidx.media3.common.Player.Listener.onVideoSizeChanged].
     *
     * Deliberately not derived from [badges], which carries the selected track's declared width and
     * height but no pixel aspect: broadcast SD is routinely anamorphic (720x576 samples showing a
     * 4:3 picture), so those two numbers alone describe a shape the viewer never sees. Consumed by
     * Picture-in-Picture, which has to size a real window - see
     * [com.uacastplayer.ui.player.PipController.aspectRatioFor]. [VideoSize.UNKNOWN] until the first
     * frame is decoded, and for audio-only channels. */
    val videoSize: VideoSize = VideoSize.UNKNOWN,
    val nextChannelsPreview: List<IndexedChannel> = emptyList(),
    val audioTracks: List<SelectableTrack> = emptyList(),
    val textTracks: List<SelectableTrack> = emptyList(),
    val canSeek: Boolean = false,
    val fatalError: Boolean = false,
    /** Mirrors the view model's private isCasting flag so the UI can decide things like
     * keepScreenOn - the phone screen doesn't need to stay awake for a receiver it isn't driving. */
    val isCasting: Boolean = false,
    /**
     * What to tell the user about a cast that is not playing, already resolved - see
     * [com.uacastplayer.cast.CastStatusMessagePolicy].
     *
     * One field rather than the five pieces of cast state it is derived from. Those were mirrored
     * here individually and combined by a `when` in the composable, which is where their precedence
     * silently went wrong; nothing else on this screen ever read them separately.
     */
    val castStatusMessage: CastStatusMessage? = null,
    val resizeMode: PlayerResizeMode = PlayerResizeMode.DEFAULT,
    /** Whether [PlayerViewModel.requestPreviousChannel] has anywhere to go - false until a second
     * distinct channel has ever loaded this session. */
    val hasPreviousChannel: Boolean = false,
    /** True from the moment a [StallRetryPolicy] recovery is scheduled until playback is confirmed
     * genuinely healthy again (see [StallDetectionPolicy.Result.inGracePeriod]) - the UI shows an
     * unobtrusive "recovering" indicator instead of the empty/frozen frame the silent stall itself
     * would otherwise leave on screen. */
    val isRecoveringPlayback: Boolean = false,
    /** Mirrors [StallRetryPolicy.State.attempt] for the current recovery streak - once it reaches
     * [StallRetryPolicy.CHANNEL_PICKER_HINT_ATTEMPT] the UI adds a "pick another channel" escape
     * hatch alongside the automatic retries, which never stop on their own. */
    val stallRecoveryAttempt: Int = 0,
)
