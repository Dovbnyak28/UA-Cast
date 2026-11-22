package app.zorik.player.parentalcontrol

import app.zorik.player.core.json.JsonDecodeResult
import app.zorik.player.core.json.MiniJson
import app.zorik.player.core.json.jsonDecodeResult

private const val FIELD_CHANNEL_KEY = "channelKey"

/** Persists the parental-control locked-channel set - just a flat list of
 * [app.zorik.player.favorites.FavoriteKey] strings, one per record, following the same
 * array-of-objects shape [app.zorik.player.playlist.GroupVisibilityCodec]/`FavoritesJsonCodec` use
 * (rather than a bespoke plain-string-array format) for consistency with the rest of the
 * MiniJson-based stores. */
object LockedChannelsCodec {

    fun encode(keys: Set<String>): String =
        MiniJson.writeArrayOfObjects(keys) { key -> linkedMapOf(FIELD_CHANNEL_KEY to key) }

    fun decode(json: String): Set<String> = when (val result = decodeResult(json)) {
        is JsonDecodeResult.Success -> result.value
        is JsonDecodeResult.Malformed -> emptySet()
    }

    internal fun decodeResult(json: String): JsonDecodeResult<Set<String>> = jsonDecodeResult {
        MiniJson.parseArrayOfObjects(json) { it[FIELD_CHANNEL_KEY] }.toSet()
    }
}
