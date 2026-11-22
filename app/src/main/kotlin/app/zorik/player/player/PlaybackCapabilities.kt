package app.zorik.player.player

import androidx.media3.common.Player

/** Effective commands, shared by the UI projection and the MediaSession boundary. */
data class PlaybackCapabilities(
    val canPlayPause: Boolean,
    val canPrepare: Boolean,
    val canStop: Boolean,
    val canSeek: Boolean,
    val canGoNext: Boolean,
    val canGoPrevious: Boolean,
)

/** Owner policy is independent of Media3's single-item timeline and command availability. */
internal data class SessionPlayerControls(
    val canPlay: Boolean,
    val canStop: Boolean,
    val canSeek: Boolean,
    val canGoNext: Boolean,
    val canGoPrevious: Boolean,
) {
    fun resolve(isCommandAvailable: (Int) -> Boolean): PlaybackCapabilities = PlaybackCapabilities(
        canPlayPause = canPlay && isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
        canPrepare = canPlay && isCommandAvailable(Player.COMMAND_PREPARE),
        canStop = canStop,
        canSeek = canSeek && isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
        // These are application playlist commands, not seeks on Media3's singleton timeline.
        canGoNext = canGoNext,
        canGoPrevious = canGoPrevious,
    )
}
