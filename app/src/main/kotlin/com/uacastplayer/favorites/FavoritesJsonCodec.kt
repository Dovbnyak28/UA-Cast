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
                    "addedAtMillis" to favorite.addedAtMillis.toString(),
                )
            }
        )

    fun decode(json: String): List<FavoriteChannel> = try {
        MiniJson.parseArrayOfObjects(json).mapNotNull { fields ->
            val key = fields["key"] ?: return@mapNotNull null
            val displayName = fields["displayName"] ?: return@mapNotNull null
            val streamUrl = fields["streamUrl"] ?: return@mapNotNull null
            val addedAtMillis = fields["addedAtMillis"]?.toLongOrNull() ?: 0L
            FavoriteChannel(key, displayName, streamUrl, fields["tvgId"], fields["groupTitle"], addedAtMillis)
        }
    } catch (_: Exception) {
        emptyList()
    }
}
