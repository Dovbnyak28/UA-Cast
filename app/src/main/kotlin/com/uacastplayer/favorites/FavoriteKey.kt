package com.uacastplayer.favorites

import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.playlist.M3uChannel

/**
 * tvg-id is stable across playlist reloads when present; otherwise name+SHA-256(streamUrl) stands
 * in for it. Either way, the key is derived only from the channel's own data - never from which
 * playlist source it was loaded from - so it stays stable across switching between multiple saved
 * playlist sources (see PlaylistSourceStore) too, not just across reloads of the same one. A
 * favorite added while Playlist A is active still matches the "same" channel if it also appears in
 * Playlist B.
 */
object FavoriteKey {
    fun of(channel: M3uChannel): String {
        val tvgId = channel.tvgId
        return if (!tvgId.isNullOrBlank()) tvgId else "${channel.displayName}:${Fingerprint.of(channel.streamUrl)}"
    }

    /** Same identity as [of], but does not hash URLs of names that cannot match a single key. */
    fun matches(channel: M3uChannel, key: String): Boolean {
        val tvgId = channel.tvgId
        return if (!tvgId.isNullOrBlank()) {
            tvgId == key
        } else {
            // Do not split on ':': names can contain it, and explicit IDs can look like fallback
            // keys. Only reject impossible candidates; the original key remains authoritative.
            key.startsWith(channel.displayName) &&
                key.getOrNull(channel.displayName.length) == ':' && of(channel) == key
        }
    }
}
