package com.uacastplayer.player

import androidx.media3.common.TrackGroup
import com.uacastplayer.playlist.M3uChannel

data class IndexedChannel(val index: Int, val channel: M3uChannel)

data class PlaybackBadgesState(
    val qualityLabel: String? = null,
    val videoCodecLabel: String? = null,
    val audioCodecLabel: String? = null,
    val channelLayout: AudioChannelLayout? = null,
)

data class SelectableTrack(
    val trackGroup: TrackGroup,
    val indexInGroup: Int,
    val label: String,
    val isSelected: Boolean,
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
)
