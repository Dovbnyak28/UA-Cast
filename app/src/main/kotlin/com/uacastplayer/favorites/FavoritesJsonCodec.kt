package com.uacastplayer.favorites

import com.uacastplayer.core.json.JsonDecodeResult
import com.uacastplayer.core.json.MiniJson
import com.uacastplayer.core.json.jsonDecodeResult

object FavoritesJsonCodec {

    fun encode(favorites: List<FavoriteChannel>): String =
        MiniJson.writeArrayOfObjects(favorites) { favorite ->
            linkedMapOf(
                "key" to favorite.key,
                "displayName" to favorite.displayName,
                "streamUrl" to favorite.streamUrl,
                "tvgId" to favorite.tvgId,
                "groupTitle" to favorite.groupTitle,
                "addedAtMillis" to favorite.addedAtMillis.toString(),
                "tvgName" to favorite.tvgName,
                "tvgLogo" to favorite.tvgLogo,
                "userAgent" to favorite.userAgent,
                "referrer" to favorite.referrer,
            )
        }

    fun decode(json: String): List<FavoriteChannel> = when (val result = decodeResult(json)) {
        is JsonDecodeResult.Success -> result.value
        is JsonDecodeResult.Malformed -> emptyList()
    }

    internal fun decodeResult(json: String): JsonDecodeResult<List<FavoriteChannel>> = jsonDecodeResult {
        MiniJson.parseArrayOfObjects(json, ::favoriteFromFields)
    }

    private fun favoriteFromFields(fields: Map<String, String?>): FavoriteChannel? {
        val key = fields["key"]
        val displayName = fields["displayName"]
        val streamUrl = fields["streamUrl"]
        if (key == null || displayName == null || streamUrl == null) return null
        val addedAtMillis = fields["addedAtMillis"]?.toLongOrNull() ?: 0L
        return FavoriteChannel(
            key, displayName, streamUrl, fields["tvgId"], fields["groupTitle"], addedAtMillis,
            fields["tvgName"], fields["tvgLogo"], fields["userAgent"], fields["referrer"],
        )
    }
}
