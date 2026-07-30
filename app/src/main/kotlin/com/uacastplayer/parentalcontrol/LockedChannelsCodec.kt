package com.uacastplayer.parentalcontrol

import com.uacastplayer.favorites.MiniJson

private const val FIELD_CHANNEL_KEY = "channelKey"

/** Persists the parental-control locked-channel set - just a flat list of
 * [com.uacastplayer.favorites.FavoriteKey] strings, one per record, following the same
 * array-of-objects shape [com.uacastplayer.playlist.GroupVisibilityCodec]/`FavoritesJsonCodec` use
 * (rather than a bespoke plain-string-array format) for consistency with the rest of the
 * MiniJson-based stores. */
object LockedChannelsCodec {

    fun encode(keys: Set<String>): String =
        MiniJson.writeArrayOfObjects(keys.map { key -> linkedMapOf(FIELD_CHANNEL_KEY to key) })

    fun decode(json: String): Set<String> = try {
        MiniJson.parseArrayOfObjects(json).mapNotNull { it[FIELD_CHANNEL_KEY] }.toSet()
    } catch (_: Exception) {
        emptySet()
    }
}
