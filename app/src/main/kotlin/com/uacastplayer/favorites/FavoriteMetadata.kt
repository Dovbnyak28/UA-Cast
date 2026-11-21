package com.uacastplayer.favorites

import com.uacastplayer.playlist.M3uChannel

/** Repair pre-metadata favorites only from the same stream, never a similarly named provider. */
object FavoriteMetadata {
    fun enrich(favorites: List<FavoriteChannel>, channels: List<M3uChannel>): List<FavoriteChannel> {
        if (favorites.isEmpty()) return favorites
        val wanted = favorites.mapTo(HashSet(favorites.size)) { it.streamUrl }
        val byUrl = channels.asSequence().filter { it.streamUrl in wanted }.associateBy { it.streamUrl }
        return favorites.map { favorite ->
            val channel = byUrl[favorite.streamUrl]
            favorite.copy(
                tvgName = channel?.tvgName ?: favorite.tvgName,
                tvgLogo = channel?.tvgLogo ?: favorite.tvgLogo,
                userAgent = channel?.userAgent ?: favorite.userAgent,
                referrer = channel?.referrer ?: favorite.referrer,
            )
        }
    }
}
