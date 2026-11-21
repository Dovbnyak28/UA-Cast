package com.uacastplayer.app

import com.uacastplayer.settings.IconSourceAddError
import com.uacastplayer.settings.SettingsUiState
import com.uacastplayer.icons.CustomIconSourcePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Validates and applies custom icon-source edits while keeping [SettingsUiState] authoritative. */
class CustomIconSettingsController(
    private val iconSources: IconSourceController,
    private val settingsState: MutableStateFlow<SettingsUiState>,
) {
    fun add(rawUrl: String) {
        val url = CustomIconSourcePolicy.canonicalize(rawUrl)
        val existing = iconSources.urls()
        val error = when {
            url == null -> IconSourceAddError.INVALID_URL
            url in existing -> IconSourceAddError.ALREADY_ADDED
            existing.size >= CustomIconSourcePolicy.MAX_SOURCES -> IconSourceAddError.LIMIT_REACHED
            else -> null
        }
        if (error != null) {
            settingsState.update { it.copy(iconSourceAddError = error) }
            return
        }
        iconSources.add(checkNotNull(url))
        settingsState.update {
            it.copy(customIconSources = iconSources.urls(), iconSourceAddError = null)
        }
    }

    fun remove(url: String) {
        iconSources.remove(url)
        settingsState.update { it.copy(customIconSources = iconSources.urls()) }
    }

    fun dismissError() {
        settingsState.update { it.copy(iconSourceAddError = null) }
    }
}
