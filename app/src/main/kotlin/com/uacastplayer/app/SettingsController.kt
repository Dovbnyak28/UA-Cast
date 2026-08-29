package com.uacastplayer.app

import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.core.settings.BufferSize
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.favorites.FavoritesSortOrder
import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.core.settings.ListDensity
import com.uacastplayer.performance.DeviceTier
import com.uacastplayer.performance.HeapBudget
import com.uacastplayer.performance.DeviceTierDefaults
import com.uacastplayer.settings.CacheSizes
import com.uacastplayer.settings.IconPlaceholdersBannerPolicy
import com.uacastplayer.settings.SettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns [SettingsUiState] - moved out of [com.uacastplayer.AppViewModel] as a move-only split (see
 * B1 in the consolidated fix plan); behavior is unchanged. Custom icon-source editing is delegated
 * to [customIcons], while its result remains part of this single [SettingsUiState]. Playlist/EPG
 * stay outside this class: [recomputeDeviceTierDefaults] takes their content counts as plain
 * parameters, preserving a one-way dependency.
 */
class SettingsController(
    private val preferences: AppPreferences,
    iconSources: IconSourceController,
    private val baseDeviceTier: DeviceTier,
) {
    private val _settingsState = MutableStateFlow(
        SettingsUiState(
            iconDisplayMode = resolvedIconDisplayMode(baseDeviceTier),
            iconDisplayModeIsAutomatic = !preferences.hasChosenIconDisplayMode,
            showIconTierBanner = iconTierBannerVisible(resolvedIconDisplayMode(baseDeviceTier)),
            listDensity = resolvedListDensity(baseDeviceTier),
            channelLayout = preferences.channelLayout,
            bufferSize = preferences.effectiveBufferSize,
            favoritesSortOrder = preferences.favoritesSortOrder,
            customIconSources = iconSources.urls(),
            wrapAroundEnabled = preferences.wrapAroundEnabled,
            autoSkipDeadEnabled = preferences.autoSkipDeadEnabled,
            deviceTier = baseDeviceTier,
        )
    )
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()
    val customIcons = CustomIconSettingsController(iconSources, _settingsState)

    fun setIconDisplayMode(mode: IconDisplayMode) {
        preferences.iconDisplayMode = mode
        // Writing the preference above makes hasChosenIconDisplayMode true from here on, so this
        // is no longer a tier default no matter what mode was picked - the banner/caption for it
        // must disappear immediately, not just next time isTierDefault happens to get recomputed.
        _settingsState.update {
            it.copy(iconDisplayMode = mode, iconDisplayModeIsAutomatic = false, showIconTierBanner = false)
        }
    }

    /** One-time dismissal of the Channels-tab tier-default icon banner - see [IconPlaceholdersBannerPolicy]. */
    fun dismissIconTierBanner() {
        preferences.hasSeenIconTierHint = true
        _settingsState.update { it.copy(showIconTierBanner = false) }
    }

    private fun iconTierBannerVisible(mode: IconDisplayMode): Boolean =
        IconPlaceholdersBannerPolicy.shouldShow(
            iconDisplayMode = mode,
            isTierDefault = !preferences.hasChosenIconDisplayMode,
            hasSeenHint = preferences.hasSeenIconTierHint,
        )

    fun setListDensity(density: ListDensity) {
        preferences.listDensity = density
        _settingsState.update { it.copy(listDensity = density) }
    }

    /** [DeviceTierDefaults] only apply until the user picks explicitly;
     * [AppPreferences.hasChosenIconDisplayMode] tracks that. */
    private fun resolvedIconDisplayMode(tier: DeviceTier): IconDisplayMode =
        if (preferences.hasChosenIconDisplayMode) {
            preferences.iconDisplayMode
        } else {
            DeviceTierDefaults.iconDisplayMode(tier)
        }

    private fun resolvedListDensity(tier: DeviceTier): ListDensity =
        if (preferences.hasChosenListDensity) preferences.listDensity else DeviceTierDefaults.listDensity(tier)

    fun recomputeDeviceTierDefaults(effectiveTier: DeviceTier) {
        val iconDisplayMode = resolvedIconDisplayMode(effectiveTier)
        _settingsState.update {
            it.copy(
                deviceTier = effectiveTier,
                iconDisplayMode = iconDisplayMode,
                iconDisplayModeIsAutomatic = !preferences.hasChosenIconDisplayMode,
                showIconTierBanner = iconTierBannerVisible(iconDisplayMode),
                listDensity = resolvedListDensity(effectiveTier),
            )
        }
    }

    fun setChannelLayout(layout: ChannelLayout) {
        preferences.channelLayout = layout
        _settingsState.update { it.copy(channelLayout = layout) }
    }

    fun setBufferSize(size: BufferSize) {
        preferences.bufferSize = size
        _settingsState.update { it.copy(bufferSize = size) }
    }

    fun setFavoritesSortOrder(order: FavoritesSortOrder) {
        preferences.favoritesSortOrder = order
        _settingsState.update { it.copy(favoritesSortOrder = order) }
    }

    fun setWrapAroundEnabled(enabled: Boolean) {
        preferences.wrapAroundEnabled = enabled
        _settingsState.update { it.copy(wrapAroundEnabled = enabled) }
    }

    fun setAutoSkipDeadEnabled(enabled: Boolean) {
        preferences.autoSkipDeadEnabled = enabled
        _settingsState.update { it.copy(autoSkipDeadEnabled = enabled) }
    }

    fun updateCacheSizes(sizes: CacheSizes) {
        _settingsState.update { it.copy(cacheSizes = sizes) }
    }
}
