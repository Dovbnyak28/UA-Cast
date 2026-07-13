package com.uacastplayer.settings

import com.uacastplayer.data.prefs.ChannelLayout
import com.uacastplayer.data.prefs.FavoritesSortOrder
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.data.prefs.ListDensity
import com.uacastplayer.performance.DeviceTier

data class CacheSizes(
    val playlistBytes: Long = 0L,
    val epgBytes: Long = 0L,
    val iconCacheBytes: Long = 0L,
    val coilCacheBytes: Long = 0L,
)

data class SettingsUiState(
    val iconDisplayMode: IconDisplayMode = IconDisplayMode.DEFAULT,
    val listDensity: ListDensity = ListDensity.DEFAULT,
    val channelLayout: ChannelLayout = ChannelLayout.DEFAULT,
    val favoritesSortOrder: FavoritesSortOrder = FavoritesSortOrder.DEFAULT,
    val wrapAroundEnabled: Boolean = true,
    val autoSkipDeadEnabled: Boolean = true,
    val cacheSizes: CacheSizes = CacheSizes(),
    val deviceTier: DeviceTier = DeviceTier.MID_RANGE,
)

enum class CacheKind { PLAYLIST, EPG, ICONS, COIL }
