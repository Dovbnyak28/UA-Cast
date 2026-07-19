package com.uacastplayer

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.i18n.withAppLocale
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.player.PlayerContainerStateMachine
import java.time.LocalDate
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.components.BatteryOptimizationDialog
import com.uacastplayer.ui.components.DownloadStatusBanner
import com.uacastplayer.ui.language.LanguagePickerScreen
import com.uacastplayer.ui.legal.HelpScreen
import com.uacastplayer.ui.legal.TermsScreen
import com.uacastplayer.ui.nav.RootScaffold
import com.uacastplayer.ui.playlist.AddPlaylistScreen
import com.uacastplayer.ui.player.PlayerHost
import com.uacastplayer.ui.theme.GlassTabBarHeight
import com.uacastplayer.ui.theme.GlassTabBarVerticalPadding
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaCastTheme

private data class PlayerRequest(val channels: List<M3uChannel>, val startIndex: Int)

/** The Bundle-safe remnant of a [PlayerRequest] that survives process death - just the playing
 * channel's stable key (see [FavoriteKey]), never the channel list itself, which can be
 * megabytes for large playlists and would risk a TransactionTooLargeException. */
private data class SavedPlayerRequest(val channelKey: String, val startIndex: Int)

private val SavedPlayerRequestSaver: Saver<SavedPlayerRequest?, List<Any>> = Saver(
    save = { request -> request?.let { listOf(it.channelKey, it.startIndex) } ?: emptyList() },
    restore = { saved ->
        if (saved.isEmpty()) null else SavedPlayerRequest(saved[0] as String, saved[1] as Int)
    },
)

/**
 * A plain ComponentActivity crashes the Cast SDK's MediaRouteButton, so this must stay a
 * FragmentActivity even though the app itself doesn't otherwise use fragments.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private var activeLanguage: AppLanguage? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        activeLanguage = viewModel.uiState.value.language

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val playlistState by viewModel.playlistState.collectAsStateWithLifecycle()
            val playlistSources by viewModel.playlistSources.collectAsStateWithLifecycle()
            val activePlaylistSourceId by viewModel.activePlaylistSourceId.collectAsStateWithLifecycle()
            val pinnedGroupKeys by viewModel.pinnedGroupKeys.collectAsStateWithLifecycle()
            val hiddenGroupKeys by viewModel.hiddenGroupKeys.collectAsStateWithLifecycle()
            val epgState by viewModel.epgState.collectAsStateWithLifecycle()
            val iconPrefetchState by viewModel.iconPrefetchState.collectAsStateWithLifecycle()
            val castState by viewModel.castState.collectAsStateWithLifecycle()
            val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
            val favorites by viewModel.favorites.collectAsStateWithLifecycle()
            val lastWatchedChannelKey by viewModel.lastWatchedChannelKey.collectAsStateWithLifecycle()
            val showBatteryOptimizationHint by viewModel.showBatteryOptimizationHint.collectAsStateWithLifecycle()
            val backupImportSummary by viewModel.backupImportSummary.collectAsStateWithLifecycle()

            val pickPlaylistFile = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let(viewModel::loadPlaylistFromFile) }
            val exportBackupFile = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri -> uri?.let(viewModel::exportBackupTo) }
            val importBackupFile = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let(viewModel::importBackupFrom) }

            var playerRequest by remember { mutableStateOf<PlayerRequest?>(null) }
            var playerContainerState by rememberSaveable {
                mutableStateOf(PlayerContainerStateMachine.State.CLOSED)
            }
            var savedPlayerRequest by rememberSaveable(stateSaver = SavedPlayerRequestSaver) {
                mutableStateOf<SavedPlayerRequest?>(null)
            }
            val openPlayer = { channels: List<M3uChannel>, startIndex: Int ->
                playerRequest = PlayerRequest(channels, startIndex)
                playerContainerState =
                    PlayerContainerStateMachine.reduce(playerContainerState, PlayerContainerStateMachine.Event.Open)
            }
            val closePlayer = {
                playerRequest = null
                playerContainerState = PlayerContainerStateMachine.State.CLOSED
                // Home's "continue watching" card only needs to catch up here, not reactively -
                // AppPreferences isn't itself observable, and refreshing on every write from
                // PlayerViewModel would be needless churn while the player is still open anyway.
                viewModel.refreshLastWatchedChannel()
            }
            var showHelp by remember { mutableStateOf(false) }
            var showTerms by remember { mutableStateOf(false) }
            var showAddPlaylist by remember { mutableStateOf(false) }
            // Incremented (never reset) each time a playlist load finishes from AddPlaylistScreen,
            // so RootScaffold's LaunchedEffect(token) fires again even if the value happened to
            // repeat - it's a one-shot "switch to Channels" signal, not a persisted tab selection.
            var focusChannelsToken by remember { mutableStateOf(0) }

            LaunchedEffect(uiState.language) {
                val previous = activeLanguage
                activeLanguage = uiState.language
                if (previous != null && previous != uiState.language) {
                    recreate()
                }
            }

            // Mirrors the live playerRequest into the Bundle-safe form on every open/close, so the
            // saved state always reflects "what would need re-opening if the process dies right
            // now" without ever holding the channel list itself.
            LaunchedEffect(playerRequest) {
                val request = playerRequest
                savedPlayerRequest = if (request == null) {
                    null
                } else {
                    request.channels.getOrNull(request.startIndex)?.let { channel ->
                        SavedPlayerRequest(FavoriteKey.of(channel), request.startIndex)
                    }
                }
            }

            // After process death, playerRequest starts out null but savedPlayerRequest may still
            // hold the channel that was playing - re-open it once the restored playlist has loaded
            // far enough to find it by key. The original sub-list (e.g. a specific group or search
            // results) isn't recoverable, so this falls back to the full flat playlist; if the
            // channel is gone entirely, the saved marker is dropped and the player just stays closed.
            LaunchedEffect(savedPlayerRequest, playlistState.groups) {
                val saved = savedPlayerRequest ?: return@LaunchedEffect
                if (playerRequest != null || !playlistState.hasChannels) return@LaunchedEffect
                val flatChannels = playlistState.groups.flatMap { it.channels }
                val index = flatChannels.indexOfFirst { FavoriteKey.of(it) == saved.channelKey }
                if (index >= 0) {
                    // playerContainerState is itself rememberSaveable, so the Expanded/Collapsed
                    // layout the user left it in normally survives process death on its own - this
                    // only needs to force it open if that somehow didn't happen (fresh state).
                    playerRequest = PlayerRequest(flatChannels, index)
                    if (playerContainerState == PlayerContainerStateMachine.State.CLOSED) {
                        playerContainerState = PlayerContainerStateMachine.reduce(
                            playerContainerState,
                            PlayerContainerStateMachine.Event.Open,
                        )
                    }
                } else {
                    savedPlayerRequest = null
                }
            }

            UaCastTheme(theme = uiState.appTheme) {
                val request = playerRequest
                val isPlayerExpanded =
                    request != null && playerContainerState == PlayerContainerStateMachine.State.EXPANDED
                val isPlayerCollapsed =
                    request != null && playerContainerState == PlayerContainerStateMachine.State.COLLAPSED

                Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.needsLanguagePicker ->
                        LanguagePickerScreen(onLanguageConfirmed = viewModel::selectLanguage)

                    uiState.needsTermsAcceptance ->
                        TermsScreen(onAccept = viewModel::acceptTerms, onDecline = { finish() })

                    showHelp -> {
                        BackHandler { showHelp = false }
                        HelpScreen(onBackClick = { showHelp = false })
                    }

                    showTerms -> {
                        BackHandler { showTerms = false }
                        TermsScreen(onBackClick = { showTerms = false })
                    }

                    showAddPlaylist -> {
                        BackHandler { showAddPlaylist = false }
                        AddPlaylistScreen(
                            playlistState = playlistState,
                            onSetDisplayName = viewModel::setPlaylistDisplayName,
                            onLoadUrl = viewModel::loadPlaylistFromUrl,
                            onPickFile = { pickPlaylistFile.launch(arrayOf("audio/x-mpegurl", "*/*")) },
                            onLoadXtream = viewModel::loadXtreamPlaylist,
                            onBackClick = { showAddPlaylist = false },
                            onPlaylistLoaded = {
                                showAddPlaylist = false
                                focusChannelsToken++
                            },
                        )
                    }

                    // Stays mounted (including while the fullscreen player covers it as an opaque
                    // overlay below) rather than being replaced by an `isPlayerExpanded` branch -
                    // RootScaffold owns its own bottom-tab selection as local state, which would
                    // otherwise reset to Home every time the player expands/collapses, since that
                    // state doesn't survive this composable leaving and re-entering composition.
                    else -> Box(modifier = Modifier.fillMaxSize()) {
                        RootScaffold(
                            currentLanguage = uiState.language,
                            onLanguageSelected = viewModel::selectLanguage,
                            currentAppTheme = uiState.appTheme,
                            onAppThemeSelected = viewModel::selectAppTheme,
                            onExitApp = { finish() },
                            playlistState = playlistState,
                            onOpenAddPlaylist = { showAddPlaylist = true },
                            onRefreshPlaylist = viewModel::refreshPlaylist,
                            playlistSources = playlistSources,
                            activePlaylistSourceId = activePlaylistSourceId,
                            onSwitchPlaylistSource = viewModel::switchPlaylistSource,
                            onRemovePlaylistSource = { viewModel.removePlaylistSource(it.id) },
                            pinnedGroupKeys = pinnedGroupKeys,
                            hiddenGroupKeys = hiddenGroupKeys,
                            onPinGroup = viewModel::pinGroup,
                            onHideGroup = viewModel::hideGroup,
                            onRestoreGroup = viewModel::clearGroupOverride,
                            focusChannelsToken = focusChannelsToken,
                            onChannelSelected = { channels, startIndex -> openPlayer(channels, startIndex) },
                            epgState = epgState,
                            onEpgSourceSelected = viewModel::selectEpgSource,
                            onUseSuggestedEpgUrl = viewModel::useSuggestedEpgUrl,
                            iconPrefetchState = iconPrefetchState,
                            onIconWifiOnlyChanged = viewModel::setIconWifiOnly,
                            resolveIcon = viewModel::resolveChannelIcon,
                            cachedIconFile = viewModel::cachedChannelIcon,
                            castState = castState,
                            settingsState = settingsState,
                            onIconDisplayModeSelected = viewModel::setIconDisplayMode,
                            onDismissIconTierBanner = viewModel::dismissIconTierBanner,
                            onListDensitySelected = viewModel::setListDensity,
                            onChannelLayoutSelected = viewModel::setChannelLayout,
                            onBufferSizeSelected = viewModel::setBufferSize,
                            onFavoritesSortOrderSelected = viewModel::setFavoritesSortOrder,
                            onWrapAroundChanged = viewModel::setWrapAroundEnabled,
                            onAutoSkipChanged = viewModel::setAutoSkipDeadEnabled,
                            onClearCache = viewModel::clearCache,
                            onExportBackup = {
                                exportBackupFile.launch("ua-cast-backup-${LocalDate.now()}.json")
                            },
                            onImportBackup = {
                                importBackupFile.launch(arrayOf("application/json", "*/*"))
                            },
                            backupImportSummary = backupImportSummary,
                            onDismissBackupImportSummary = viewModel::dismissBackupImportSummary,
                            favorites = favorites,
                            lastWatchedChannelKey = lastWatchedChannelKey,
                            isFavorite = viewModel::isFavorite,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onRemoveFavorite = viewModel::removeFavorite,
                            onReorderFavorites = viewModel::reorderFavorites,
                            onOpenBatteryOptimizationHint = viewModel::reopenBatteryOptimizationHint,
                            onAddIconSource = viewModel::addCustomIconSource,
                            onRemoveIconSource = viewModel::removeCustomIconSource,
                            onDismissIconSourceError = viewModel::dismissIconSourceError,
                            onOpenHelp = { showHelp = true },
                            onOpenTerms = { showTerms = true },
                        )
                        DownloadStatusBanner(
                            iconPrefetchState = iconPrefetchState,
                            epgState = epgState,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }

                // A single stable PlayerHost call site, always composed whenever a channel is
                // loaded regardless of layout, drawn as a later sibling so it overlays whatever the
                // `when` above is showing - this must never be duplicated into a second call site
                // (e.g. one per branch), since PlayerHost owns its own NavHost/PlayerViewModel/
                // ExoPlayer and a second call site would mean a second, independent instance of all
                // three. Its own modifier decides fullscreen (opaque, covers RootScaffold - see its
                // mounting comment above) vs the small floating bar; the bar is a plain overlay, not
                // routed through RootScaffold's Scaffold, for the same "one call site" reason.
                if (request != null) {
                    BackHandler(enabled = isPlayerExpanded) {
                        playerContainerState = PlayerContainerStateMachine.reduce(
                            playerContainerState,
                            PlayerContainerStateMachine.Event.Back,
                        )
                    }
                    // Disabled while a sheet-like screen (Help/Terms/AddPlaylist) has its own
                    // BackHandler active, so back closes that first - otherwise this collapsed-bar
                    // handler, composed later, would intercept back before the sheet's own does.
                    BackHandler(enabled = isPlayerCollapsed && !showHelp && !showTerms && !showAddPlaylist) {
                        closePlayer()
                    }
                    PlayerHost(
                        channels = request.channels,
                        startIndex = request.startIndex,
                        collapsed = !isPlayerExpanded,
                        onExit = closePlayer,
                        onTapCollapsed = {
                            playerContainerState = PlayerContainerStateMachine.reduce(
                                playerContainerState,
                                PlayerContainerStateMachine.Event.Tap,
                            )
                        },
                        isFavorite = viewModel::isFavorite,
                        onToggleFavorite = viewModel::toggleFavorite,
                        resolveIcon = viewModel::resolveChannelIcon,
                        epgState = epgState,
                        iconPrefetchState = iconPrefetchState,
                        modifier = if (isPlayerExpanded) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(horizontal = ScreenHPadding)
                                .padding(bottom = GlassTabBarHeight + GlassTabBarVerticalPadding * 2)
                        },
                    )
                }

                if (showBatteryOptimizationHint) {
                    BatteryOptimizationDialog(
                        onAllow = viewModel::dismissBatteryOptimizationHint,
                        onDismiss = viewModel::dismissBatteryOptimizationHint,
                    )
                }
                }
            }
        }
    }
}
