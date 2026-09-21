package com.uacastplayer.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride

/** Applies user track choices without exposing Media3 selection plumbing to the UI layer. */
class PlayerTrackController(private val player: Player) {
    fun selectAudio(track: SelectableTrack) = select(track, C.TRACK_TYPE_AUDIO)

    fun selectText(track: SelectableTrack) = select(track, C.TRACK_TYPE_TEXT)

    fun clearText() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private fun select(track: SelectableTrack, trackType: Int) {
        val override = TrackSelectionOverride(track.trackGroup, track.indexInGroup)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(trackType, false)
            .setOverrideForType(override)
            .build()
    }
}
