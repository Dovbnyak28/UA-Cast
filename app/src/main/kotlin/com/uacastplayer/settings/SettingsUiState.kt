package com.uacastplayer.settings

import com.uacastplayer.core.settings.BufferSize
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.favorites.FavoritesSortOrder
import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.core.settings.ListDensity
import com.uacastplayer.performance.DeviceTier

data class CacheSizes(
    val playlistBytes: Long = 0L,
    val epgBytes: Long = 0L,
    val iconCacheBytes: Long = 0L,
    val coilCacheBytes: Long = 0L,
)

data class SettingsUiState(
    val iconDisplayMode: IconDisplayMode = IconDisplayMode.DEFAULT,
    // True when iconDisplayMode came from DeviceTierDefaults rather than an explicit user choice
    // (see AppPreferences.hasChosenIconDisplayMode) - drives both the Settings-screen caption and
    // showIconTierBanner below (see IconPlaceholdersBannerPolicy).
    val iconDisplayModeIsAutomatic: Boolean = false,
    val showIconTierBanner: Boolean = false,
    val listDensity: ListDensity = ListDensity.DEFAULT,
    val channelLayout: ChannelLayout = ChannelLayout.DEFAULT,
    val bufferSize: BufferSize = BufferSize.DEFAULT,
    val favoritesSortOrder: FavoritesSortOrder = FavoritesSortOrder.DEFAULT,
    val wrapAroundEnabled: Boolean = true,
    val autoSkipDeadEnabled: Boolean = true,
    val cacheSizes: CacheSizes = CacheSizes(),
    val deviceTier: DeviceTier = DeviceTier.MID_RANGE,
    val customIconSources: List<String> = emptyList(),
    val iconSourceAddError: IconSourceAddError? = null,
)

enum class CacheKind { PLAYLIST, EPG, ICONS, COIL }

/** Why [com.uacastplayer.AppViewModel.addCustomIconSource] rejected the entered URL. */
enum class IconSourceAddError { INVALID_URL, ALREADY_ADDED, LIMIT_REACHED }
