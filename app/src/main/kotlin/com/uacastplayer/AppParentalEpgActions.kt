package com.uacastplayer

import com.uacastplayer.epg.EpgSource
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.playlist.M3uChannel

internal fun AppViewModel.isChannelLocked(channel: M3uChannel): Boolean =
    parentalControlController.isLocked(FavoriteKey.of(channel))

internal fun AppViewModel.lockChannel(channel: M3uChannel) =
    parentalControlController.lockChannel(FavoriteKey.of(channel))

internal fun AppViewModel.unlockChannelPermanently(channel: M3uChannel) =
    parentalControlController.unlockChannelPermanently(FavoriteKey.of(channel))

internal suspend fun AppViewModel.verifyParentalControlPin(pin: String): Boolean =
    parentalControlController.verifyPin(pin)

internal suspend fun AppViewModel.setParentalControlPin(pin: String): Boolean =
    parentalControlController.setPin(pin)

internal fun AppViewModel.resetParentalControl() = parentalControlController.resetParentalControl()

internal fun AppViewModel.selectEpgSource(source: EpgSource) = epgController.selectEpgSource(source)

internal fun AppViewModel.useSuggestedEpgUrl() = epgController.useSuggestedEpgUrl()
