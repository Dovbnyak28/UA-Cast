package com.uacastplayer.app

import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.data.prefs.BufferSize
import com.uacastplayer.data.prefs.ChannelLayout
import com.uacastplayer.data.prefs.FavoritesSortOrder
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.data.prefs.ListDensity
import com.uacastplayer.performance.DeviceTier
import com.uacastplayer.performance.HeapBudget
import com.uacastplayer.performance.DeviceTierDefaults
import com.uacastplayer.settings.CacheSizes
import com.uacastplayer.settings.IconPlaceholdersBannerPolicy
import com.uacastplayer.settings.IconSourceAddError
import com.uacastplayer.settings.SettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns [SettingsUiState] - moved out of [com.uacastplayer.AppViewModel] as a move-only split (see
 * B1 in the consolidated fix plan); behavior is unchanged. Depends on [iconController] for custom
 * icon sources (there's no separate icon-settings state, it's just [SettingsUiState] fields), but
 * doesn't depend on playlist/EPG - [recomputeDeviceTierDefaults] takes their content counts as
 * plain parameters instead, so this stays a one-way dependency.
 */
class SettingsController(
    private val preferences: AppPreferences,
    private val iconController: IconController,
    private val baseDeviceTier: DeviceTier,
) {
    private val _settingsState = MutableStateFlow(
        SettingsUiState(
            iconDisplayMode = resolvedIconDisplayMode(baseDeviceTier),
            iconDisplayModeIsAutomatic = !preferences.hasChosenIconDisplayMode,
            showIconTierBanner = iconTierBannerVisible(resolvedIconDisplayMode(baseDeviceTier)),
            listDensity = resolvedListDensity(baseDeviceTier),
            channelLayout = preferences.channelLayout,
            bufferSize = resolvedBufferSize(),
            favoritesSortOrder = preferences.favoritesSortOrder,
            customIconSources = iconController.customIconSources(),
            wrapAroundEnabled = preferences.wrapAroundEnabled,
            autoSkipDeadEnabled = preferences.autoSkipDeadEnabled,
            deviceTier = baseDeviceTier,
        )
    )
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

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

    /**
     * The media buffer, defaulted from the heap this app was actually given rather than fixed at
     * MEDIUM for every device.
     *
     * Not a function of the device *tier*, unlike the two above, and that is the point: the tier is
     * scored from RAM, cores and API level, all of which the phone this came from scores well on
     * while being handed a 128MB heap. See [com.uacastplayer.performance.HeapBudget].
     *
     * Read once at construction rather than recomputed with the tier, because unlike the tier this
     * cannot change while the app runs - a heap limit is fixed for the life of the process.
     */
    private fun resolvedBufferSize(): BufferSize =
        if (preferences.hasChosenBufferSize) {
            preferences.bufferSize
        } else {
            HeapBudget.defaultBufferSize(Runtime.getRuntime().maxMemory())
        }

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

    fun addCustomIconSource(rawUrl: String) {
        val url = rawUrl.trim()
        val isHttpUrl = url.startsWith("http://") || url.startsWith("https://")
        val error = when {
            !isHttpUrl -> IconSourceAddError.INVALID_URL
            url in iconController.customIconSources() -> IconSourceAddError.ALREADY_ADDED
            else -> null
        }
        if (error != null) {
            _settingsState.update { it.copy(iconSourceAddError = error) }
            return
        }
        iconController.addCustomIconSource(url)
        _settingsState.update {
            it.copy(customIconSources = iconController.customIconSources(), iconSourceAddError = null)
        }
    }

    fun removeCustomIconSource(url: String) {
        iconController.removeCustomIconSource(url)
        _settingsState.update { it.copy(customIconSources = iconController.customIconSources()) }
    }

    fun dismissIconSourceError() {
        _settingsState.update { it.copy(iconSourceAddError = null) }
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
