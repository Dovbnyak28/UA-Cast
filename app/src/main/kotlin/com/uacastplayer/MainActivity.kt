package com.uacastplayer

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.i18n.withAppLocale
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.player.PlayerContainerStateMachine
import java.time.LocalDate
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import androidx.compose.ui.platform.LocalUriHandler
import com.uacastplayer.log.AppLog
import com.uacastplayer.ui.components.BatteryOptimizationDialog
import com.uacastplayer.ui.components.ParentalControlPinDialog
import com.uacastplayer.ui.language.LanguagePickerScreen
import com.uacastplayer.update.UpdateSectionState
import com.uacastplayer.ui.legal.HelpScreen
import com.uacastplayer.ui.legal.TermsScreen
import com.uacastplayer.ui.onboarding.OnboardingScreen
import com.uacastplayer.ui.nav.RootScaffold
import com.uacastplayer.ui.permissions.NotificationPermissionGate
import com.uacastplayer.ui.playlist.AddPlaylistScreen
import com.uacastplayer.ui.player.PlayerEnrichmentState
import com.uacastplayer.ui.player.PlayerFavoriteActions
import com.uacastplayer.ui.player.PlayerHost
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.GlassTabBarHeight
import com.uacastplayer.ui.theme.GlassTabBarVerticalPadding
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaCastTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        // Opting in rather than waiting to be opted in. From targetSdk 35 the system forces
        // edge-to-edge and ignores android:statusBarColor/navigationBarColor outright, so on
        // Android 15+ this happens whether the app asks or not - and the difference between an app
        // that handles it and one that doesn't is content sliding under the system bars. Declaring
        // it here means the layout runs the same way on every API level, so a missing
        // windowInsetsPadding shows up on any test device instead of only on Android 15 hardware.
        // Both bars are transparent with no scrim: every screen is drawn on the app's own dark
        // background, and the chrome that meets the bars (RootTopBar, GlassTabBar, the mini player)
        // pads itself out of them and paints its own background behind them.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        activeLanguage = viewModel.uiState.value.language

        setContent {
            // Only the routing/theme-gate fields of AppUiState are read at this top level - every
            // other flow (playlist, EPG, icons, cast, settings, favorites, ...) used to be collected
            // here too, which meant a change to *any* of them recomposed this entire tree, including
            // the player container and every dialog. They're now collected inside whichever zone
            // composable below actually consumes them (see ScaffoldZone/PlayerZone/BatteryHintZone),
            // so e.g. an icon-prefetch progress tick no longer has anything to do with the player.
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            // Set true only by OnboardingScreen's final step, so MainAppContent knows to open
            // AddPlaylist immediately - "Skip" (any step) and system back both leave this false,
            // going straight into the ordinary app instead. Never reset once true; MainAppContent
            // reads it exactly once, on its own first composition.
            var openAddPlaylistAfterOnboarding by remember { mutableStateOf(false) }

            LaunchedEffect(uiState.language) {
                val previous = activeLanguage
                activeLanguage = uiState.language
                if (previous != null && previous != uiState.language) {
                    recreate()
                }
            }

            UaCastTheme(theme = uiState.appTheme) {
                when {
                    uiState.needsLanguagePicker ->
                        LanguagePickerScreen(onLanguageConfirmed = viewModel::selectLanguage)

                    uiState.needsTermsAcceptance ->
                        TermsScreen(onAccept = viewModel::acceptTerms, onDecline = { finish() })

                    uiState.needsOnboarding ->
                        OnboardingScreen(onFinished = { openAddPlaylist ->
                            openAddPlaylistAfterOnboarding = openAddPlaylist
                            viewModel.completeOnboarding()
                        })

                    else -> MainAppContent(
                        viewModel = viewModel,
                        currentLanguage = uiState.language,
                        currentAppTheme = uiState.appTheme,
                        onFinish = { finish() },
                        startWithAddPlaylist = openAddPlaylistAfterOnboarding,
                    )
                }
            }
        }
    }
}

/**
 * Everything past the onboarding gate: the main scaffold (Home/Channels/Favorites/Settings tabs),
 * the always-mounted player container, and the battery-optimization dialog - three independent
 * recomposition scopes (see [ScaffoldZone]/[PlayerZone]/[BatteryHintZone]) sharing only the small
 * bits of local UI state ([playerRequest]/[playerContainerState] and the sheet-visibility flags)
 * that genuinely span more than one of them.
 */
@Composable
private fun MainAppContent(
    viewModel: AppViewModel,
    currentLanguage: AppLanguage,
    currentAppTheme: AppTheme,
    onFinish: () -> Unit,
    startWithAddPlaylist: Boolean = false,
) {
    val playlistState by viewModel.playlistState.collectAsStateWithLifecycle()

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
    val openPlayerReal = { channels: List<M3uChannel>, startIndex: Int ->
        playerRequest = PlayerRequest(channels, startIndex)
        playerContainerState =
            PlayerContainerStateMachine.reduce(playerContainerState, PlayerContainerStateMachine.Event.Open)
    }

    // See rememberParentalControlGate's doc - a locked channel's playback goes through the same
    // "run this once PIN-unlocked" gate that unlocking a channel from ChannelActionsSheet and
    // Settings' locked-channel management/PIN-change rows all use.
    val requireParentalControlUnlock = rememberParentalControlGate(viewModel)

    val openPlayer = { channels: List<M3uChannel>, startIndex: Int ->
        val channel = channels.getOrNull(startIndex)
        if (channel != null && viewModel.isChannelLocked(channel)) {
            requireParentalControlUnlock { openPlayerReal(channels, startIndex) }
        } else {
            openPlayerReal(channels, startIndex)
        }
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
    var showAddPlaylist by remember { mutableStateOf(startWithAddPlaylist) }
    // Incremented (never reset) each time a playlist load finishes from AddPlaylistScreen, so
    // RootScaffold's LaunchedEffect(token) fires again even if the value happened to repeat - it's
    // a one-shot "switch to Channels" signal, not a persisted tab selection.
    var focusChannelsToken by remember { mutableIntStateOf(0) }

    // Mirrors the live playerRequest into the Bundle-safe form on every open/close, so the saved
    // state always reflects "what would need re-opening if the process dies right now" without
    // ever holding the channel list itself.
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

    // After process death, playerRequest starts out null but savedPlayerRequest may still hold the
    // channel that was playing - re-open it once the restored playlist has loaded far enough to
    // find it by key. The original sub-list (e.g. a specific group or search results) isn't
    // recoverable, so this falls back to the full flat playlist; if the channel is gone entirely,
    // the saved marker is dropped and the player just stays closed.
    LaunchedEffect(savedPlayerRequest, playlistState.groups) {
        val saved = savedPlayerRequest ?: return@LaunchedEffect
        if (playerRequest != null || !playlistState.hasChannels) return@LaunchedEffect
        // Off the main thread: FavoriteKey.of is a SHA-256 per channel without a tvg-id, and this
        // scans the entire flattened playlist - tens of thousands of channels on a large provider
        // list, at exactly the moment the app is being restored and the user is waiting on a frame.
        val (flatChannels, index) = withContext(Dispatchers.Default) {
            val flat = playlistState.groups.flatMap { it.channels }
            flat to flat.indexOfFirst { FavoriteKey.of(it) == saved.channelKey }
        }
        if (index >= 0) {
            // playerContainerState is itself rememberSaveable, so the Expanded/Collapsed layout the
            // user left it in normally survives process death on its own - this only needs to force
            // it open if that somehow didn't happen (fresh state).
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

    Box(modifier = Modifier.fillMaxSize()) {
        ScaffoldZone(
            viewModel = viewModel,
            playlistState = playlistState,
            currentLanguage = currentLanguage,
            currentAppTheme = currentAppTheme,
            onExitApp = onFinish,
            showHelp = showHelp,
            showTerms = showTerms,
            showAddPlaylist = showAddPlaylist,
            focusChannelsToken = focusChannelsToken,
            onOpenHelp = { showHelp = true },
            onOpenTerms = { showTerms = true },
            onOpenAddPlaylist = { showAddPlaylist = true },
            onCloseHelp = { showHelp = false },
            onCloseTerms = { showTerms = false },
            onCloseAddPlaylist = { showAddPlaylist = false },
            onChannelSelected = openPlayer,
            onPlaylistLoaded = {
                showAddPlaylist = false
                focusChannelsToken++
            },
            pickPlaylistFile = { pickPlaylistFile.launch(arrayOf("audio/x-mpegurl", "*/*")) },
            exportBackupFile = { exportBackupFile.launch("ua-cast-backup-${LocalDate.now()}.json") },
            importBackupFile = { importBackupFile.launch(arrayOf("application/json", "*/*")) },
            requireParentalControlUnlock = requireParentalControlUnlock,
        )

        // A single stable PlayerHost call site, always composed whenever a channel is loaded
        // regardless of layout, drawn as a later sibling so it overlays whatever ScaffoldZone is
        // showing - this must never be duplicated into a second call site (e.g. one per branch),
        // since PlayerHost owns its own NavHost/PlayerViewModel/ExoPlayer and a second call site
        // would mean a second, independent instance of all three.
        PlayerZone(
            viewModel = viewModel,
            playerRequest = playerRequest,
            playerContainerState = playerContainerState,
            onPlayerContainerStateChange = { playerContainerState = it },
            onClosePlayer = closePlayer,
            backHandlerBlocked = showHelp || showTerms || showAddPlaylist,
        )

        BatteryHintZone(viewModel = viewModel)
    }
}

/**
 * Returns a function that runs its argument immediately if the parental-control PIN was already
 * entered this app session, or stashes it and shows the PIN dialog otherwise - the argument then
 * runs once the PIN checks out. Self-contained: also renders the dialog itself, so a caller just
 * wraps whatever needs gating (opening a locked channel, unlocking one permanently, Settings'
 * locked-channel management/PIN-change rows) in the returned function and nothing else. See
 * `app/ParentalControlController`'s doc for why unlocking (unlike locking) always needs this.
 */
@Composable
private fun rememberParentalControlGate(viewModel: AppViewModel): (() -> Unit) -> Unit {
    var pendingUnlockAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }
    val unlocked by viewModel.parentalControlUnlocked.collectAsStateWithLifecycle()
    // Read through a holder rather than captured directly, so the returned gate below can be
    // remembered once instead of being reallocated whenever `unlocked` flips.
    val unlockedNow = rememberUpdatedState(unlocked)
    val pinScope = rememberCoroutineScope()

    if (showDialog) {
        ParentalControlPinDialog(
            title = stringResource(R.string.parental_control_enter_pin),
            isError = pinError,
            // Launched rather than called inline: verifying runs PBKDF2 off the main thread now
            // (see ParentalControlController.verifyPin), so the result arrives a frame or two later.
            onSubmit = { pin ->
                pinScope.launch {
                    if (viewModel.verifyParentalControlPin(pin)) {
                        showDialog = false
                        pinError = false
                        pendingUnlockAction?.invoke()
                        pendingUnlockAction = null
                    } else {
                        pinError = true
                    }
                }
            },
            onDismiss = {
                showDialog = false
                pendingUnlockAction = null
            },
        )
    }

    // Remembered, not rebuilt per composition: this is passed all the way down into RootScaffold's
    // ~50-parameter call, and an identity that changed on every recomposition meant that call could
    // never be skipped - one EPG minute tick recomposed the entire tab scaffold.
    return remember {
        { action: () -> Unit ->
            if (unlockedNow.value) {
                action()
            } else {
                pendingUnlockAction = action
                pinError = false
                showDialog = true
            }
        }
    }
}

/** The main tab scaffold (Home/Channels/Favorites/Settings) plus the Help/Terms/AddPlaylist
 * full-screen overlays that temporarily replace it - collects every [RootScaffold] dependency
 * itself so a change to any of them (icon prefetch progress, cast state, EPG ticks, ...) only
 * recomposes this zone, not [PlayerZone] or [BatteryHintZone]. */
@Composable
@Suppress("LongParameterList") // glue for RootScaffold's own (out-of-scope-to-restructure) signature
private fun ScaffoldZone(
    viewModel: AppViewModel,
    playlistState: PlaylistUiState,
    currentLanguage: AppLanguage,
    currentAppTheme: AppTheme,
    onExitApp: () -> Unit,
    showHelp: Boolean,
    showTerms: Boolean,
    showAddPlaylist: Boolean,
    focusChannelsToken: Int,
    onOpenHelp: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenAddPlaylist: () -> Unit,
    onCloseHelp: () -> Unit,
    onCloseTerms: () -> Unit,
    onCloseAddPlaylist: () -> Unit,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    onPlaylistLoaded: () -> Unit,
    pickPlaylistFile: () -> Unit,
    exportBackupFile: () -> Unit,
    importBackupFile: () -> Unit,
    requireParentalControlUnlock: (() -> Unit) -> Unit,
) {
    val playlistSources by viewModel.playlistSources.collectAsStateWithLifecycle()
    val activePlaylistSourceId by viewModel.activePlaylistSourceId.collectAsStateWithLifecycle()
    val pinnedGroupKeys by viewModel.pinnedGroupKeys.collectAsStateWithLifecycle()
    val hiddenGroupKeys by viewModel.hiddenGroupKeys.collectAsStateWithLifecycle()
    val lockedChannelKeys by viewModel.lockedChannelKeys.collectAsStateWithLifecycle()
    val parentalControlPinSet by viewModel.parentalControlPinSet.collectAsStateWithLifecycle()
    val epgState by viewModel.epgState.collectAsStateWithLifecycle()
    val iconPrefetchState by viewModel.iconPrefetchState.collectAsStateWithLifecycle()
    val castState by viewModel.castState.collectAsStateWithLifecycle()
    // Asks for POST_NOTIFICATIONS the moment a cast starts, so CastProxyService's foreground
    // notification is actually visible on API 33+ - see NotificationPermissionGate.
    NotificationPermissionGate(castConnected = castState.isSessionConnected)
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val lastWatchedChannelKey by viewModel.lastWatchedChannelKey.collectAsStateWithLifecycle()
    val backupImportSummary by viewModel.backupImportSummary.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    // LocalUriHandler rather than a raw ACTION_VIEW intent: it needs no queries entry in the
    // manifest, and it is the one place a device with no browser at all - a TV box, say - would
    // otherwise throw on what is meant to be an optional convenience.
    val uriHandler = LocalUriHandler.current
    val updateSection = UpdateSectionState(
        state = updateState,
        onCheckNow = viewModel::checkForUpdatesNow,
        onOpenRelease = { url ->
            try {
                uriHandler.openUri(url)
            } catch (e: IllegalArgumentException) {
                AppLog.w("MainActivity") { "no app can open the release page: ${e.javaClass.simpleName}" }
            }
        },
        onDismissBanner = viewModel::dismissUpdateBanner,
        onOutcomeShown = viewModel::clearUpdateCheckOutcome,
    )

    // Derived from the flows collected right above rather than delegated to viewModel::isFavorite /
    // viewModel::isChannelLocked, which read StateFlow.value directly - a plain function call is not
    // a Compose state read, so a row's star/lock icon had no subscription to what it renders and
    // only ever refreshed because some ancestor happened to recompose the whole subtree anyway. Any
    // future skippability win in RootScaffold/ChannelsScreen would have silently frozen both badges
    // with no compile error to catch it. Keying the lambdas on the sets also gives Compose a real
    // signal that these inputs changed. HashSet, not the List: isFavorite runs once per visible row
    // per recomposition, and the favorites list is user-grown and unbounded.
    val favoriteKeys = remember(favorites) { favorites.mapTo(HashSet(favorites.size)) { it.key } }
    val isFavorite = remember(favoriteKeys) {
        { channel: M3uChannel -> FavoriteKey.of(channel) in favoriteKeys }
    }
    val isChannelLocked = remember(lockedChannelKeys) {
        { channel: M3uChannel -> FavoriteKey.of(channel) in lockedChannelKeys }
    }
    // Cast routing counters only ever move during a cast session, so castState is the narrowest
    // signal that can mean "these may have changed". Called unkeyed, this ran on every recomposition
    // of this zone - i.e. on every EPG minute tick and every icon-prefetch progress update - while
    // still never actually refreshing reactively.
    val remuxEffectiveness = remember(castState) { viewModel.remuxEffectivenessSnapshot() }

    when {
        showHelp -> {
            BackHandler { onCloseHelp() }
            HelpScreen(onBackClick = onCloseHelp, onBuildDiagnosticsReport = viewModel::buildDiagnosticsReport)
        }

        showTerms -> {
            BackHandler { onCloseTerms() }
            TermsScreen(onBackClick = onCloseTerms)
        }

        showAddPlaylist -> {
            BackHandler { onCloseAddPlaylist() }
            AddPlaylistScreen(
                playlistState = playlistState,
                onSetDisplayName = viewModel::setPlaylistDisplayName,
                onLoadUrl = viewModel::loadPlaylistFromUrl,
                onPickFile = pickPlaylistFile,
                onLoadXtream = viewModel::loadXtreamPlaylist,
                onBackClick = onCloseAddPlaylist,
                onPlaylistLoaded = onPlaylistLoaded,
            )
        }

        // Stays mounted (including while the fullscreen player covers it as an opaque overlay
        // below, in PlayerZone) rather than being replaced by an "expanded" branch - RootScaffold
        // owns its own bottom-tab selection as local state, which would otherwise reset to Home
        // every time the player expands/collapses, since that state doesn't survive this
        // composable leaving and re-entering composition.
        else -> RootScaffold(
            modifier = Modifier.fillMaxSize(),
            currentLanguage = currentLanguage,
            onLanguageSelected = viewModel::selectLanguage,
            currentAppTheme = currentAppTheme,
            onAppThemeSelected = viewModel::selectAppTheme,
            onExitApp = onExitApp,
            playlistState = playlistState,
            onOpenAddPlaylist = onOpenAddPlaylist,
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
            isChannelLocked = isChannelLocked,
            onLockChannel = viewModel::lockChannel,
            onUnlockChannel = { channel ->
                requireParentalControlUnlock { viewModel.unlockChannelPermanently(channel) }
            },
            lockedChannelKeys = lockedChannelKeys,
            parentalControlPinSet = parentalControlPinSet,
            // A lambda, not a method reference: Kotlin will not adapt `::suspendFun` to a
            // `suspend (String) -> Boolean` parameter type.
            onSetParentalControlPin = { pin -> viewModel.setParentalControlPin(pin) },
            onResetParentalControl = viewModel::resetParentalControl,
            requireParentalControlUnlock = requireParentalControlUnlock,
            focusChannelsToken = focusChannelsToken,
            onChannelSelected = onChannelSelected,
            epgState = epgState,
            onEpgSourceSelected = viewModel::selectEpgSource,
            onUseSuggestedEpgUrl = viewModel::useSuggestedEpgUrl,
            iconPrefetchState = iconPrefetchState,
            onIconWifiOnlyChanged = viewModel::setIconWifiOnly,
            resolveIcon = viewModel::resolveChannelIcon,
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
            onExportBackup = exportBackupFile,
            onImportBackup = importBackupFile,
            backupImportSummary = backupImportSummary,
            onDismissBackupImportSummary = viewModel::dismissBackupImportSummary,
            favorites = favorites,
            lastWatchedChannelKey = lastWatchedChannelKey,
            isFavorite = isFavorite,
            onToggleFavorite = viewModel::toggleFavorite,
            onRemoveFavorite = viewModel::removeFavorite,
            onReorderFavorites = viewModel::reorderFavorites,
            onOpenBatteryOptimizationHint = viewModel::reopenBatteryOptimizationHint,
            onAddIconSource = viewModel::addCustomIconSource,
            onRemoveIconSource = viewModel::removeCustomIconSource,
            onDismissIconSourceError = viewModel::dismissIconSourceError,
            onOpenHelp = onOpenHelp,
            onOpenTerms = onOpenTerms,
            remuxEffectiveness = remuxEffectiveness,
            updateSection = updateSection,
        )
    }
}

/** The always-mounted player container - collects only what [PlayerHost] itself needs, so cast
 * state, settings, the backup summary etc. (all owned by [ScaffoldZone]) never touch this scope. */
@Composable
private fun BoxScope.PlayerZone(
    viewModel: AppViewModel,
    playerRequest: PlayerRequest?,
    playerContainerState: PlayerContainerStateMachine.State,
    onPlayerContainerStateChange: (PlayerContainerStateMachine.State) -> Unit,
    onClosePlayer: () -> Unit,
    backHandlerBlocked: Boolean,
) {
    val request = playerRequest ?: return
    val epgState by viewModel.epgState.collectAsStateWithLifecycle()
    val iconPrefetchState by viewModel.iconPrefetchState.collectAsStateWithLifecycle()
    // Collected here, rather than delegating to viewModel::isFavorite, for the same reason as
    // ScaffoldZone's copy: PlayerScreen's favorite button tints itself from isFavorite(currentChannel),
    // and a plain function call reading StateFlow.value is not a Compose state read. Without a
    // subscription the star kept its old tint after being tapped until something unrelated
    // recomposed this zone - in practice the next EPG minute tick, so up to a minute late. Unlike
    // the flows deliberately kept out of this scope, favorites only change when the user actually
    // toggles one, which is exactly when the player *should* recompose.
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteKeys = remember(favorites) { favorites.mapTo(HashSet(favorites.size)) { it.key } }
    val isFavorite = remember(favoriteKeys) {
        { channel: M3uChannel -> FavoriteKey.of(channel) in favoriteKeys }
    }
    val isPlayerExpanded = playerContainerState == PlayerContainerStateMachine.State.EXPANDED
    val isPlayerCollapsed = playerContainerState == PlayerContainerStateMachine.State.COLLAPSED

    BackHandler(enabled = isPlayerExpanded) {
        onPlayerContainerStateChange(
            PlayerContainerStateMachine.reduce(playerContainerState, PlayerContainerStateMachine.Event.Back),
        )
    }
    // Disabled while a sheet-like screen (Help/Terms/AddPlaylist) has its own BackHandler active, so
    // back closes that first - otherwise this collapsed-bar handler, composed later, would
    // intercept back before the sheet's own does.
    BackHandler(enabled = isPlayerCollapsed && !backHandlerBlocked) {
        onClosePlayer()
    }
    PlayerHost(
        channels = request.channels,
        startIndex = request.startIndex,
        collapsed = !isPlayerExpanded,
        onExit = onClosePlayer,
        onTapCollapsed = {
            onPlayerContainerStateChange(
                PlayerContainerStateMachine.reduce(playerContainerState, PlayerContainerStateMachine.Event.Tap),
            )
        },
        resolveIcon = viewModel::resolveChannelIcon,
        castArtworkUrl = viewModel::castArtworkUrlFor,
        favoriteActions = PlayerFavoriteActions(
            isFavorite = isFavorite,
            onToggleFavorite = viewModel::toggleFavorite,
        ),
        enrichment = PlayerEnrichmentState(epgState = epgState, iconPrefetchState = iconPrefetchState),
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

/** Collects only [AppViewModel.showBatteryOptimizationHint] - the rest of the app's state has
 * nothing to do with whether this dialog is showing. */
@Composable
private fun BatteryHintZone(viewModel: AppViewModel) {
    val showBatteryOptimizationHint by viewModel.showBatteryOptimizationHint.collectAsStateWithLifecycle()
    if (showBatteryOptimizationHint) {
        BatteryOptimizationDialog(
            onAllow = viewModel::dismissBatteryOptimizationHint,
            onDismiss = viewModel::dismissBatteryOptimizationHint,
        )
    }
}
