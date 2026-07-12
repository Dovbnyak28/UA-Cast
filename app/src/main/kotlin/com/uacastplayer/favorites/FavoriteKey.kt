package com.uacastplayer.favorites

import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.playlist.M3uChannel

/** tvg-id is stable across playlist reloads when present; otherwise name+SHA-256(streamUrl) stands in for it. */
object FavoriteKey {
    fun of(channel: M3uChannel): String {
        val tvgId = channel.tvgId
        return if (!tvgId.isNullOrBlank()) tvgId else "${channel.displayName}:${Fingerprint.of(channel.streamUrl)}"
    }
}
