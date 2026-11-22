package app.zorik.player.player

import app.zorik.player.playlist.M3uChannel
import app.zorik.player.media.LocalVideo

/** One explicit user request. Its identity distinguishes a reattached UI from another tap. */
class PlayerRequest(val channels: List<M3uChannel>, val startIndex: Int) {
    companion object {
        fun forLocalVideo(video: LocalVideo): PlayerRequest =
            PlayerRequest(listOf(M3uChannel(video.displayName, video.uri, localVideo = video)), 0)
    }
}
