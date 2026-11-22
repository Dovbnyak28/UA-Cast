package app.zorik.player

import app.zorik.player.core.settings.BufferSize
import app.zorik.player.core.settings.ChannelLayout
import app.zorik.player.favorites.FavoritesSortOrder
import app.zorik.player.core.settings.IconDisplayMode
import app.zorik.player.core.settings.ListDensity

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

internal fun AppViewModel.setReducedVisualEffects(enabled: Boolean) =
    settingsController.setReducedVisualEffects(enabled)
