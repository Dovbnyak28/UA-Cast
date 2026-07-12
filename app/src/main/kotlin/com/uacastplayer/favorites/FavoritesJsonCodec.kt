package com.uacastplayer.favorites

object FavoritesJsonCodec {

    fun encode(favorites: List<FavoriteChannel>): String =
        MiniJson.writeArrayOfObjects(
            favorites.map { favorite ->
                linkedMapOf(
                    "key" to favorite.key,
                    "displayName" to favorite.displayName,
                    "streamUrl" to favorite.streamUrl,
                    "tvgId" to favorite.tvgId,
                    "groupTitle" to favorite.groupTitle,
                )
            }
        )

    fun decode(json: String): List<FavoriteChannel> = try {
        MiniJson.parseArrayOfObjects(json).mapNotNull { fields ->
            val key = fields["key"] ?: return@mapNotNull null
            val displayName = fields["displayName"] ?: return@mapNotNull null
            val streamUrl = fields["streamUrl"] ?: return@mapNotNull null
            FavoriteChannel(key, displayName, streamUrl, fields["tvgId"], fields["groupTitle"])
        }
    } catch (_: Exception) {
        emptyList()
    }
}
