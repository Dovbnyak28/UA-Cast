package com.uacastplayer

import com.uacastplayer.core.settings.BufferSize
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.favorites.FavoritesSortOrder
import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.core.settings.ListDensity

internal fun AppViewModel.setIconDisplayMode(mode: IconDisplayMode) =
    settingsController.setIconDisplayMode(mode)

internal fun AppViewModel.dismissIconTierBanner() = settingsController.dismissIconTierBanner()

internal fun AppViewModel.setListDensity(density: ListDensity) = settingsController.setListDensity(density)

internal fun AppViewModel.setChannelLayout(layout: ChannelLayout) = settingsController.setChannelLayout(layout)

internal fun AppViewModel.setBufferSize(size: BufferSize) = settingsController.setBufferSize(size)

internal fun AppViewModel.setFavoritesSortOrder(order: FavoritesSortOrder) =
    settingsController.setFavoritesSortOrder(order)

internal fun AppViewModel.setWrapAroundEnabled(enabled: Boolean) =
    settingsController.setWrapAroundEnabled(enabled)

internal fun AppViewModel.setAutoSkipDeadEnabled(enabled: Boolean) =
    settingsController.setAutoSkipDeadEnabled(enabled)
