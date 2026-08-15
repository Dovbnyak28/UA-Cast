package com.uacastplayer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.i18n.withAppLocale
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.guidedtour.GuidedTourSectionState
import com.uacastplayer.ui.guidedtour.GuidedTourHost
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.player.PlayerContainerStateMachine
import java.time.LocalDate
import com.uacastplayer.parentalcontrol.PlayerChannelAccess
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import androidx.compose.ui.platform.LocalUriHandler
import com.uacastplayer.log.AppLog
import com.uacastplayer.ui.components.BatteryOptimizationDialog
import com.uacastplayer.ui.components.ParentalControlPinDialog
import com.uacastplayer.ui.language.LanguagePickerScreen
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.uacastplayer.core.ui.findActivity
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.data.update.ApkInstaller
import com.uacastplayer.update.UpdateSectionState
import com.uacastplayer.ui.legal.HelpScreen
import com.uacastplayer.ui.legal.TermsScreen
import com.uacastplayer.ui.nav.RootScaffold
import com.uacastplayer.ui.permissions.NotificationPermissionGate
import com.uacastplayer.ui.playlist.AddPlaylistScreen
import com.uacastplayer.ui.player.PlayerEnrichmentState
import com.uacastplayer.ui.player.PlayerFavoriteActions
import com.uacastplayer.ui.player.PlayerHost
import androidx.compose.runtime.CompositionLocalProvider
import com.uacastplayer.ui.premium.LocalFeatureGate
import com.uacastplayer.ui.premium.LocalPremiumNotice
import com.uacastplayer.ui.premium.rememberFeatureGate
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.GlassTabBarHeight
import com.uacastplayer.ui.theme.GlassTabBarVerticalPadding
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaCastTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Key for the one entry in `ScaffoldZone`'s [rememberSaveableStateHolder] - see its `else`
 * branch. There is deliberately only ever one: the Help/Terms/AddPlaylist screens are transient and
 * keep nothing, so only the tab scaffold underneath them has state worth holding on to. */
private const val ROOT_SCAFFOLD_STATE_KEY = "root-scaffold"

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

                    // No walkthrough gate here any more. The three static cards that used to sit
                    // between Terms and the app were a second explanation of what the guided tour
                    // explains by pointing at the real thing - and back to back they were four
                    // screens of reading before the first useful tap. The tour opens itself on
                    // first launch instead (see MainAppContent), over the app, where the buttons
                    // it describes actually are.
                    else -> MainAppContent(
                        viewModel = viewModel,
                        currentLanguage = uiState.language,
                        currentAppTheme = uiState.appTheme,
                        onFinish = { finish() },
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
        // Narrowed here, at the single funnel every screen's channel tap goes through, and after
        // the PIN gate below has had its say - so by the time this runs, a session unlocked by a
        // correct PIN hands the whole list over untouched. See PlayerChannelAccess: without this
        // the lock was checked against the tapped channel only, and the player's own next/previous
        // walked straight into locked channels with nothing asked.
        val playable = PlayerChannelAccess.forSession(
            channels = channels,
            startIndex = startIndex,
            lockedKeys = viewModel.lockedChannelKeys.value,
            keyOf = FavoriteKey::of,
            sessionUnlocked = viewModel.parentalControlUnlocked.value,
        )
        playerRequest = PlayerRequest(playable.channels, playable.startIndex)
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
    var showAddPlaylist by remember { mutableStateOf(false) }
    val guidedTourState by viewModel.guidedTourState.collectAsStateWithLifecycle()

    // Offered here rather than from AppViewModel's init, so it happens *after* the language and
    // terms gates rather than behind them.
    LaunchedEffect(Unit) { viewModel.offerGuidedTourOnLaunch() }
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
        // A locked channel must not come back on its own - see
        // PlayerChannelAccess.mayRestoreAfterProcessDeath. This path reopens the player without a
        // tap, so it is the one place the PIN gate can never have run.
        val mayRestore = index >= 0 && PlayerChannelAccess.mayRestoreAfterProcessDeath(
            channel = flatChannels[index],
            lockedKeys = viewModel.lockedChannelKeys.value,
            keyOf = FavoriteKey::of,
            sessionUnlocked = viewModel.parentalControlUnlocked.value,
        )
        if (mayRestore) {
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

    // Wraps everything, including the player and the dialogs: the tour is drawn over the app, and
    // the app is what it is pointing at. Placed here rather than inside ScaffoldZone so a target
    // registered anywhere - the top bar's cast button, a channel row's star - reaches the same
    // registry.
    GuidedTourHost(
        state = guidedTourState,
        onNext = viewModel::guidedTourNext,
        onBack = viewModel::guidedTourBack,
        onSkip = viewModel::guidedTourSkip,
        onComplete = viewModel::guidedTourComplete,
    ) {
    val entitlements by viewModel.entitlements.collectAsStateWithLifecycle()
    val premiumProducts by viewModel.premiumProducts.collectAsStateWithLifecycle()
    // The store's purchase flow needs an Activity to show its own UI over; findActivity() is
    // this project's existing way of reaching one from a composable.
    val activity = LocalContext.current.findActivity()

    // Asked for when a premium surface is about to be shown rather than at startup: an app whose
    // user never opens the premium screen should not be talking to a store at all.
    LaunchedEffect(Unit) { viewModel.refreshPremiumProducts() }

    // remember, not a plain construction: this holder carries a Set and lambdas, so Compose treats
    // it as unstable and compares it by identity. Built fresh on every pass it is never equal to the
    // previous one, and since ScaffoldZone recomposes whenever any of its flows emits - the EPG
    // clock ticks twice a minute on its own - that would stop SettingsScreen from ever skipping.
    // Keyed on the values it actually derives from.
    //
    // Built here rather than inside ScaffoldZone because two things need it now: the Settings
    // section, and the gate below, which is provided above every screen.
    val premiumOutcome by viewModel.lastPurchaseOutcome.collectAsStateWithLifecycle()
    val premiumConnection by viewModel.premiumConnection.collectAsStateWithLifecycle()
    val premiumSection = remember(entitlements, premiumProducts, activity, premiumOutcome, premiumConnection) {
        PremiumSectionState(
            entitlements = entitlements,
            products = premiumProducts,
            onPurchase = { product -> viewModel.purchasePremium(product, activity) },
            onRestore = viewModel::restorePremiumPurchases,
            lastOutcome = premiumOutcome,
            connection = premiumConnection,
            // Fixed for the lifetime of the process: filled in by the debug Application before any
            // composition runs, and empty forever in a release build.
            developerStates = viewModel.developerLicenseStates,
            onDeveloperStateSelected = viewModel::applyDeveloperLicenseState,
        )
    }

    // Provided here, above every screen, for the reason FeatureGate's own doc gives: an offer point
    // exists in Settings, in the playlist screens and in the cast sheet, and passing this down would
    // add two parameters to each of the signatures in between. Its surfaces are drawn as the last
    // sibling in the Box below, so the paywall lands over whatever refused the tap.
    val premiumUi = rememberFeatureGate(
        featureManager = viewModel.featureManager,
        section = premiumSection,
    )
    CompositionLocalProvider(
        LocalFeatureGate provides premiumUi.gate,
        LocalPremiumNotice provides premiumUi.notice,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        ScaffoldZone(
            viewModel = viewModel,
            playlistState = playlistState,
            guidedTourDestination = guidedTourState.currentStep?.destination,
            currentLanguage = currentLanguage,
            currentAppTheme = currentAppTheme,
            onExitApp = onFinish,
            showHelp = showHelp,
            showTerms = showTerms,
            showAddPlaylist = showAddPlaylist,
            focusChannelsToken = focusChannelsToken,
            onOpenHelp = { showHelp = true },
            onOpenTerms = { showTerms = true },
            onBuildDiagnosticsReport = viewModel::buildDiagnosticsReport,
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
            premiumSection = premiumSection,
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

        // Last, so the unlock dialog and the premium sheet sit over the screen that raised them -
        // including over the player, which is itself an overlay.
        premiumUi.overlays()
    }
    }
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
    onBuildDiagnosticsReport: () -> String,
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
    premiumSection: PremiumSectionState,
    guidedTourDestination: BottomDestination?,
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
    val updateInstallState by viewModel.updateInstallState.collectAsStateWithLifecycle()
    // Same reason as premiumSection - this reaches SettingsScreen, and a fresh instance on
    // every pass would stop it skipping.
    val hasSeenGuidedTour by viewModel.hasSeenGuidedTour.collectAsStateWithLifecycle()
    val guidedTourSection = remember(hasSeenGuidedTour) {
        GuidedTourSectionState(hasSeenTour = hasSeenGuidedTour, onStartTour = viewModel::startGuidedTour)
    }

    // LocalUriHandler rather than a raw ACTION_VIEW intent: it needs no queries entry in the
    // manifest, and it is the one place a device with no browser at all - a TV box, say - would
    // otherwise throw on what is meant to be an optional convenience.
    val uriHandler = LocalUriHandler.current
    // remembered for the same reason as premiumSection above: rebuilt on every pass it would be a
    // new instance each time, and this one reaches the top bar, so it would also re-invalidate the
    // update banner twice a minute for nothing.
    val context = LocalContext.current
    val updateSection = remember(updateState, updateInstallState, uriHandler, context) {
        UpdateSectionState(
            state = updateState,
            installState = updateInstallState,
            onCheckNow = viewModel::checkForUpdatesNow,
            onOpenRelease = { url ->
                try {
                    uriHandler.openUri(url)
                } catch (e: IllegalArgumentException) {
                    AppLog.w("MainActivity") { "no app can open the release page: ${e.javaClass.simpleName}" }
                }
            },
            onDownloadAndInstall = viewModel::downloadAndInstallUpdate,
            onGrantInstallPermission = { openInstallPermissionSettings(context) },
            onDismissBanner = viewModel::dismissUpdateBanner,
            onOutcomeShown = {
                viewModel.clearUpdateCheckOutcome()
                viewModel.clearUpdateInstallOutcome()
            },
        )
    }

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

    // Declared above the branch below, so that showing Help, Terms or the add-playlist screen does
    // not take the whole tab scaffold's state with it - see the doc on the `else` branch.
    val overlayStateHolder = rememberSaveableStateHolder()

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

        /**
         * Wrapped in a [SaveableStateProvider] because the three branches above genuinely do
         * replace it, and everything the user had set up inside went with it: the selected tab
         * (back to Home), the opened group, the group grid's scroll position, both search boxes.
         * Opening Help from Settings and pressing back landed on Home; adding a playlist from the
         * Channels tab and pressing back did the same, from a list the user had scrolled.
         *
         * `rememberSaveable` state only outlives a composable that leaves composition if something
         * holds it, and until now nothing did. The holder keeps this whole subtree's saved state -
         * including [RootScaffold]'s own per-tab holder, which is itself a `rememberSaveable` - so
         * one key restores the tab and everything that tab had on screen.
         *
         * The player is a different case and stays as it was: it never unmounts this at all (see
         * PlayerZone, drawn as a later sibling), so there is nothing to restore.
         */
        else -> overlayStateHolder.SaveableStateProvider(ROOT_SCAFFOLD_STATE_KEY) {
            RootScaffold(
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
                onBuildDiagnosticsReport = onBuildDiagnosticsReport,
                remuxEffectiveness = remuxEffectiveness,
                updateSection = updateSection,
                premiumSection = premiumSection,
                guidedTourSection = guidedTourSection,
                guidedTourDestination = guidedTourDestination,
            )
        }
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

/**
 * Opens the system screen where this app can be allowed to install packages.
 *
 * `ACTION_MANAGE_UNKNOWN_APP_SOURCES` is a documented action, not a guaranteed one - the same
 * assumption `BatteryOptimizationDialog` was crashing on before it was fixed, and for the same
 * reason: a stripped or replaced Settings app resolves nothing and `startActivity` on an
 * unresolvable intent throws `ActivityNotFoundException`, which is unchecked. Here it would land on
 * a user who has already waited out a download, so it degrades to a log line and a row that keeps
 * saying permission is needed.
 *
 * `package:` on the intent's data is what makes the system open this app's own switch rather than
 * the list of every app; without it the user has to find UA Cast in a list themselves.
 *
 * Below API 26 there is no per-app switch at all - the manifest permission is the whole of it - so
 * nothing here is ever reached on those devices (see [ApkInstaller.canInstallPackages]).
 */
private fun openInstallPermissionSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        .setData("package:${context.packageName}".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        AppLog.w("MainActivity") { "this device has no install-permission screen: ${e.javaClass.simpleName}" }
    }
}
