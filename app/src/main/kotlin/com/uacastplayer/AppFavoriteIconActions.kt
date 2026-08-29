package com.uacastplayer

import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.playlist.M3uChannel

internal fun AppViewModel.setIconWifiOnly(enabled: Boolean) = iconController.setIconWifiOnly(enabled)

internal suspend fun AppViewModel.resolveChannelIcon(channel: M3uChannel) =
    iconController.resolveChannelIcon(
        channel,
        settingsState.value.iconDisplayMode,
        epgIconUrlForAction(channel),
    )

/** Resolved per channel switch so artwork can use EPG data that arrived after playback started. */
internal fun AppViewModel.castArtworkUrlFor(channel: M3uChannel): String? =
    iconController.castArtworkUrl(channel, epgIconUrlForAction(channel))

internal fun AppViewModel.isFavorite(channel: M3uChannel): Boolean = favoritesRepository.isFavorite(channel)

internal fun AppViewModel.toggleFavorite(channel: M3uChannel) = favoritesRepository.toggleFavorite(channel)

internal fun AppViewModel.removeFavorite(key: String) = favoritesRepository.remove(key)

internal fun AppViewModel.reorderFavorites(newOrder: List<FavoriteChannel>) =
    favoritesRepository.reorder(newOrder)

private fun AppViewModel.epgIconUrlForAction(channel: M3uChannel): String? =
    epgState.value.data?.index?.match(channel)?.iconUrl
