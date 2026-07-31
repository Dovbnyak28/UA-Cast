package com.uacastplayer.player

import androidx.media3.common.Player

/**
 * Maps the subset of [Player] commands a system media session surface (headset buttons, a
 * smartwatch, the system media notification) can send into this app's own channel-switching
 * actions. ExoPlayer only ever holds a single [androidx.media3.common.MediaItem] at a time (see
 * [PlayerViewModel.switchToIndexImmediate]), so its built-in seek-to-next/previous-media-item
 * handling would be a no-op; the session callback intercepts those two commands and this policy
 * decides what they mean for a "channel" instead of a real playlist item.
 */
object MediaSessionCommandPolicy {

    enum class Action { NEXT, PREVIOUS }

    fun mapCommand(playerCommand: Int): Action? = when (playerCommand) {
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> Action.NEXT
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> Action.PREVIOUS
        else -> null
    }
}
