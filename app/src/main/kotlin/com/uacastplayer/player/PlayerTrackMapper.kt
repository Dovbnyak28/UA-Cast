package com.uacastplayer.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.IllformedLocaleException
import java.util.Locale

/** Result of translating Media3 track groups into state the Compose player can render. */
internal data class PlayerTrackSnapshot(
    val badges: PlaybackBadgesState,
    val audioTracks: List<SelectableTrack>,
    val textTracks: List<SelectableTrack>,
)

/**
 * Media3-to-UI adapter extracted from [PlayerViewModel]. The ViewModel owns state transitions;
 * this class owns the mechanical traversal and formatting of Media3's nested track groups.
 */
@UnstableApi
internal class PlayerTrackMapper(
    private val unknownTrackLabel: (Int) -> String,
) {
    fun map(tracks: Tracks): PlayerTrackSnapshot {
        var video: SelectedVideoFormat? = null
        var audio: SelectedAudioFormat? = null
        val audioTracks = mutableListOf<SelectableTrack>()
        val textTracks = mutableListOf<SelectableTrack>()

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> video = selectedVideo(group) ?: video
                C.TRACK_TYPE_AUDIO -> {
                    val mapped = mapAudio(group)
                    audioTracks += mapped.tracks
                    audio = mapped.selected ?: audio
                }
                C.TRACK_TYPE_TEXT -> textTracks += mapText(group)
            }
        }
        return PlayerTrackSnapshot(
            badges = badgesState(video, audio),
            audioTracks = audioTracks,
            textTracks = textTracks,
        )
    }

    private fun selectedVideo(group: Tracks.Group): SelectedVideoFormat? {
        for (index in 0 until group.length) {
            if (group.isTrackSelected(index)) return selectedVideoFormat(group.getTrackFormat(index))
        }
        return null
    }

    private fun mapAudio(group: Tracks.Group): AudioGroupSnapshot {
        var selected: SelectedAudioFormat? = null
        val tracks = ArrayList<SelectableTrack>(group.length)
        for (index in 0 until group.length) {
            val format = group.getTrackFormat(index)
            val isSelected = group.isTrackSelected(index)
            tracks += audioSelectableTrack(group, index, format, isSelected)
            if (isSelected) selected = selectedAudioFormat(format)
        }
        return AudioGroupSnapshot(tracks, selected)
    }

    private fun mapText(group: Tracks.Group): List<SelectableTrack> =
        List(group.length) { index -> textSelectableTrack(group, index) }

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

    private fun audioSelectableTrack(
        group: Tracks.Group,
        index: Int,
        format: Format,
        selected: Boolean,
    ): SelectableTrack {
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

    private fun trackLabel(language: String?, label: String?, index: Int): String {
        return when {
            !label.isNullOrBlank() -> label
            language.isNullOrBlank() -> unknownTrackLabel(index)
            else -> try {
                Locale.Builder().setLanguage(language).build().displayLanguage.ifBlank { language }
            } catch (_: IllformedLocaleException) {
                language
            }
        }
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

    private data class AudioGroupSnapshot(
        val tracks: List<SelectableTrack>,
        val selected: SelectedAudioFormat?,
    )

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
}
