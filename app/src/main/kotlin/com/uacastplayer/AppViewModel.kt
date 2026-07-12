package com.uacastplayer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.data.playlist.PlaylistOutcome
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppUiState(
    val language: AppLanguage = AppLanguage.DEFAULT,
    val needsLanguagePicker: Boolean = true,
)

/**
 * Root view model for app-wide, cross-screen state (language, playlist/channels, and - from
 * later stages - the EPG/favorites/icon caches). Screen-scoped state such as the player stack
 * lives in its own view model instead.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)
    private val playlistRepository = PlaylistRepository(application)

    private val _uiState = MutableStateFlow(
        AppUiState(
            language = preferences.language,
            needsLanguagePicker = !preferences.hasChosenLanguage,
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _playlistState = MutableStateFlow(PlaylistUiState())
    val playlistState: StateFlow<PlaylistUiState> = _playlistState.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.restoreSnapshot()?.let { applyOutcome(it) }
        }
    }

    fun selectLanguage(language: AppLanguage) {
        preferences.language = language
        _uiState.value = _uiState.value.copy(language = language, needsLanguagePicker = false)
    }

    fun loadPlaylistFromUrl(url: String) {
        if (url.isBlank()) return
        _playlistState.value = _playlistState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            applyOutcome(playlistRepository.loadFromUrl(url.trim()))
        }
    }

    fun loadPlaylistFromFile(uri: Uri) {
        _playlistState.value = _playlistState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            applyOutcome(playlistRepository.loadFromFile(uri))
        }
    }

    private fun applyOutcome(outcome: PlaylistOutcome) {
        _playlistState.value = when (outcome) {
            is PlaylistOutcome.Loaded -> PlaylistUiState(
                groups = outcome.groups,
                isLoading = false,
                skippedLineCount = outcome.skippedLineCount,
                error = null,
            )
            PlaylistOutcome.SizeLimitExceeded -> _playlistState.value.copy(
                isLoading = false,
                error = PlaylistError.SizeLimitExceeded,
            )
            is PlaylistOutcome.HttpError -> _playlistState.value.copy(
                isLoading = false,
                error = PlaylistError.Http(outcome.code),
            )
            is PlaylistOutcome.ReadError -> _playlistState.value.copy(
                isLoading = false,
                error = PlaylistError.Network,
            )
        }
    }
}
