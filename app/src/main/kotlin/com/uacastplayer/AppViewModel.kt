package com.uacastplayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.data.prefs.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppUiState(
    val language: AppLanguage = AppLanguage.DEFAULT,
    val needsLanguagePicker: Boolean = true,
)

/**
 * Root view model for app-wide, cross-screen state (language, and - from later stages - the
 * playlist/EPG/favorites/icon caches). Screen-scoped state such as the player stack lives in its
 * own view model instead.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)

    private val _uiState = MutableStateFlow(
        AppUiState(
            language = preferences.language,
            needsLanguagePicker = !preferences.hasChosenLanguage,
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun selectLanguage(language: AppLanguage) {
        preferences.language = language
        _uiState.value = _uiState.value.copy(language = language, needsLanguagePicker = false)
    }
}
