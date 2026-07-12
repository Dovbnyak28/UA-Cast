package com.uacastplayer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uacastplayer.cast.CastPlaybackState
import com.uacastplayer.cast.CastSessionRepository
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.data.epg.EpgOutcome
import com.uacastplayer.data.epg.EpgRepository
import com.uacastplayer.data.icons.IconPrefetcher
import com.uacastplayer.data.icons.IconRepository
import com.uacastplayer.data.playlist.PlaylistOutcome
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.icons.LogoUpdateReminder
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistUiState
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AppUiState(
    val language: AppLanguage = AppLanguage.DEFAULT,
    val needsLanguagePicker: Boolean = true,
)

private const val EPG_TICK_MILLIS = 30_000L

/**
 * Root view model for app-wide, cross-screen state (language, playlist/channels, EPG, and - from
 * later stages - favorites/icon caches). Screen-scoped state such as the player stack lives in
 * its own view model instead.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)
    private val playlistRepository = PlaylistRepository(application)
    private val epgRepository = EpgRepository(application)
    private val iconRepository = IconRepository(application)
    private val iconPrefetcher = IconPrefetcher(application, iconRepository)
    private var unmeteredNetworkWatcher: AutoCloseable? = null

    val castState: StateFlow<CastPlaybackState> = CastSessionRepository.getInstance(application).state

    private val _uiState = MutableStateFlow(
        AppUiState(
            language = preferences.language,
            needsLanguagePicker = !preferences.hasChosenLanguage,
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _playlistState = MutableStateFlow(PlaylistUiState())
    val playlistState: StateFlow<PlaylistUiState> = _playlistState.asStateFlow()

    private val _epgState = MutableStateFlow(EpgUiState(selectedSource = preferences.epgSource))
    val epgState: StateFlow<EpgUiState> = _epgState.asStateFlow()

    private val _iconPrefetchState = MutableStateFlow(
        IconPrefetchUiState(
            wifiOnly = preferences.iconWifiOnly,
            updateReminderDue = LogoUpdateReminder.isDue(preferences.lastIconPrefetchAtMillis, System.currentTimeMillis()),
        )
    )
    val iconPrefetchState: StateFlow<IconPrefetchUiState> = _iconPrefetchState.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.restoreSnapshot()?.let { applyPlaylistOutcome(it) }
        }
        viewModelScope.launch {
            (epgRepository.restoreSnapshot() as? EpgOutcome.Loaded)?.let { outcome ->
                _epgState.update { it.copy(data = outcome.data) }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(EPG_TICK_MILLIS)
                _epgState.update { it.copy(nowMillis = System.currentTimeMillis()) }
            }
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
            applyPlaylistOutcome(playlistRepository.loadFromUrl(url.trim()))
        }
    }

    fun loadPlaylistFromFile(uri: Uri) {
        _playlistState.value = _playlistState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            applyPlaylistOutcome(playlistRepository.loadFromFile(uri))
        }
    }

    fun selectEpgSource(source: EpgSource) {
        preferences.epgSource = source
        _epgState.update { it.copy(selectedSource = source, isLoading = true) }
        viewModelScope.launch {
            applyEpgOutcome(epgRepository.loadFromSource(source))
        }
    }

    fun setIconWifiOnly(enabled: Boolean) {
        preferences.iconWifiOnly = enabled
        _iconPrefetchState.update { it.copy(wifiOnly = enabled) }
    }

    suspend fun resolveChannelIcon(channel: M3uChannel): File? {
        val epgIconUrl = epgState.value.data?.index?.match(channel)?.iconUrl
        return iconRepository.resolveIconFile(channel.tvgLogo, epgIconUrl, channel.tvgId)
    }

    private var lastPrefetchChannels: List<M3uChannel> = emptyList()

    private fun triggerIconPrefetch(channels: List<M3uChannel>) {
        lastPrefetchChannels = channels
        unmeteredNetworkWatcher?.close()
        unmeteredNetworkWatcher = iconPrefetcher.awaitUnmeteredNetwork {
            viewModelScope.launch { runIconPrefetch(lastPrefetchChannels) }
        }
        viewModelScope.launch { runIconPrefetch(channels) }
    }

    private suspend fun runIconPrefetch(channels: List<M3uChannel>) {
        if (channels.isEmpty()) return
        _iconPrefetchState.update { it.copy(isRunning = true, completed = 0, total = channels.size) }
        iconPrefetcher.prefetch(channels, preferences.iconWifiOnly) { progress ->
            _iconPrefetchState.update { it.copy(completed = progress.completed, total = progress.total) }
        }
        preferences.lastIconPrefetchAtMillis = System.currentTimeMillis()
        _iconPrefetchState.update { it.copy(isRunning = false, updateReminderDue = false) }
    }

    override fun onCleared() {
        unmeteredNetworkWatcher?.close()
    }

    private fun applyPlaylistOutcome(outcome: PlaylistOutcome) {
        _playlistState.value = when (outcome) {
            is PlaylistOutcome.Loaded -> {
                triggerIconPrefetch(outcome.groups.flatMap { it.channels })
                PlaylistUiState(
                    groups = outcome.groups,
                    isLoading = false,
                    skippedLineCount = outcome.skippedLineCount,
                    error = null,
                )
            }
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

    private fun applyEpgOutcome(outcome: EpgOutcome) {
        _epgState.update { current ->
            when (outcome) {
                is EpgOutcome.Loaded -> current.copy(data = outcome.data, isLoading = false)
                else -> current.copy(isLoading = false)
            }
        }
    }
}
