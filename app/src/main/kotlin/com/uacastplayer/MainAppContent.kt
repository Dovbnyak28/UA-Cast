package com.uacastplayer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.log.AppLog
import com.uacastplayer.core.ui.findActivity
import com.uacastplayer.ui.platform.launchOrLogAbsence
import com.uacastplayer.data.playlist.withPlaylistCpu
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.player.PlayerContainerStateMachine
import com.uacastplayer.player.PlayerRequest
import com.uacastplayer.parentalcontrol.PlayerChannelAccess
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.ui.guidedtour.GuidedTourHost
import com.uacastplayer.ui.components.UpdateOfferDialog
import com.uacastplayer.ui.premium.LocalFeatureGate
import com.uacastplayer.ui.premium.LocalPremiumNotice
import com.uacastplayer.ui.premium.rememberFeatureGate
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.player.PlayerRequestViewModel
import com.uacastplayer.update.GitHubRelease
import java.time.LocalDate

/**
 * Everything past the onboarding gate: the main scaffold (Home/Channels/Favorites/Settings tabs),
 * the always-mounted player container, and the battery-optimization dialog - three independent
 * recomposition scopes (see [ScaffoldZone]/[PlayerZone]/[BatteryHintZone]) sharing only the small
 * bits of local UI state ([playerRequest]/[playerContainerState] and the sheet-visibility flags)
 * that genuinely span more than one of them.
 */
@Composable
internal fun MainAppContent(
    viewModel: AppViewModel,
    currentLanguage: AppLanguage,
    currentAppTheme: AppTheme,
    onFinish: () -> Unit,
) {
    val playlistState by viewModel.playlistState.collectAsStateWithLifecycle()
    val parentalReady by viewModel.parentalControlReady.collectAsStateWithLifecycle()
    val lockedKeys by viewModel.lockedChannelKeys.collectAsStateWithLifecycle()
    val sessionUnlocked by viewModel.parentalControlUnlocked.collectAsStateWithLifecycle()

    val pickPlaylistFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::loadPlaylistFromFile) }
    val exportBackupFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportBackupTo) }
    val importBackupFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importBackupFrom) }

    val requestOwner = androidx.lifecycle.viewmodel.compose.viewModel<PlayerRequestViewModel>()
    val playerRequest by requestOwner.request.collectAsStateWithLifecycle()
    var pendingOpen by remember { mutableStateOf<PlayerRequest?>(null) }
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
        val request = PlayerRequest(playable.channels, playable.startIndex)
        requestOwner.open(request)
        // Update the saveable marker at the user action, not in an effect of playerRequest.
        // On recreation the in-memory request starts null; an effect mirroring that null used
        // to erase the restored marker before the restoration effect below could consume it.
        savedPlayerRequest = request.toSavedRequest()
        playerContainerState =
            PlayerContainerStateMachine.reduce(playerContainerState, PlayerContainerStateMachine.Event.Open)
    }

    // See rememberParentalControlGate's doc - a locked channel's playback goes through the same
    // "run this once PIN-unlocked" gate that unlocking a channel from ChannelActionsSheet and
    // Settings' locked-channel management/PIN-change rows all use.
    val requireParentalControlUnlock = rememberParentalControlGate(viewModel)

    val openPlayer = { channels: List<M3uChannel>, startIndex: Int ->
        pendingOpen = PlayerRequest(channels, startIndex)
    }
    OpenPlayerWhenReady(
        pendingOpen, parentalReady, { pendingOpen = null }, viewModel::isChannelLocked, requireParentalControlUnlock,
    ) { request ->
        openPlayerReal(request.channels, request.startIndex)
    }
    val closePlayer = {
        pendingOpen = null
        requestOwner.close()
        savedPlayerRequest = null
        playerContainerState = PlayerContainerStateMachine.State.CLOSED
        // Home's "continue watching" card only needs to catch up here, not reactively -
        // AppPreferences isn't itself observable, and refreshing on every write from
        // PlayerViewModel would be needless churn while the player is still open anyway.
        viewModel.refreshLastWatchedChannel()
    }
    var showHelp by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showAddPlaylist by remember { mutableStateOf(false) }
    val guidedTourState by viewModel.guidedTourState.collectAsStateWithLifecycle()
    val hasSeenGuidedTour by viewModel.hasSeenGuidedTour.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    // Offered here rather than from AppViewModel's init, so it happens *after* the language and
    // terms gates rather than behind them.
    LaunchedEffect(Unit) { viewModel.offerGuidedTourOnLaunch() }
    // Incremented (never reset) each time a playlist load finishes from AddPlaylistScreen, so
    // RootScaffold's LaunchedEffect(token) fires again even if the value happened to repeat - it's
    // a one-shot "switch to Channels" signal, not a persisted tab selection.
    var focusChannelsToken by remember { mutableIntStateOf(0) }

    // After process death, playerRequest starts out null but savedPlayerRequest may still hold the
    // channel that was playing - re-open it once the restored playlist has loaded far enough to
    // find it by key. The original sub-list (e.g. a specific group or search results) isn't
    // recoverable, so this falls back to the full flat playlist; if the channel is gone entirely,
    // the saved marker is dropped and the player just stays closed.
    LaunchedEffect(savedPlayerRequest, playlistState.groups, parentalReady, lockedKeys, sessionUnlocked) {
        val saved = savedPlayerRequest ?: return@LaunchedEffect
        if (requestOwner.request.value != null || !playlistState.hasChannels || !parentalReady) return@LaunchedEffect
        // Off the main thread: FavoriteKey.of is a SHA-256 per channel without a tvg-id, and this
        // scans the entire flattened playlist - tens of thousands of channels on a large provider
        // list, at exactly the moment the app is being restored and the user is waiting on a frame.
        val restored = withPlaylistCpu {
            // PlayerViewModel persists every switch, while AppViewModel's StateFlow is refreshed
            // only when the player closes. Prefer that synchronous value so a process death or a
            // rotation after Next/Previous restores the channel that was actually on screen, not
            // the channel that originally opened this PlayerHost.
            val restoreKey = saved.preferredChannelKey(viewModel.persistedLastWatchedChannelKey())
            PlayerChannelAccess.forRestore(
                playlistState.channels, restoreKey, lockedKeys, FavoriteKey::of, sessionUnlocked, parentalReady,
            )
        }
        // A new open/close can arrive during the CPU hop. It must win over this old restore.
        if (savedPlayerRequest != saved || requestOwner.request.value != null) return@LaunchedEffect
        // A locked channel must not come back on its own - see
        // PlayerChannelAccess.mayRestoreAfterProcessDeath. This path reopens the player without a
        // tap, so it is the one place the PIN gate can never have run.
        if (restored != null) {
            // playerContainerState is itself rememberSaveable, so the Expanded/Collapsed layout the
            // user left it in normally survives process death on its own - this only needs to force
            // it open if that somehow didn't happen (fresh state).
            requestOwner.open(PlayerRequest(restored.channels, restored.startIndex))
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
    // registered anywhere - the player Cast button, a channel row's star - reaches the same
    // registry.
    GuidedTourHost(
        state = guidedTourState,
        onNext = viewModel::guidedTourNext,
        onBack = viewModel::guidedTourBack,
        onSkip = viewModel::guidedTourSkip,
        onAddPlaylist = {
            viewModel.guidedTourSkip()
            showAddPlaylist = true
        },
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
        val premiumPurchasing by viewModel.isPurchasing.collectAsStateWithLifecycle()
        val premiumConnection by viewModel.premiumConnection.collectAsStateWithLifecycle()
        val premiumSection = remember(
            entitlements,
            premiumProducts,
            activity,
            premiumOutcome,
            premiumPurchasing,
            premiumConnection,
        ) {
            PremiumSectionState(
                entitlements = entitlements,
                products = premiumProducts,
                onPurchase = { product -> viewModel.purchasePremium(product, activity) },
                onRestore = viewModel::restorePremiumPurchases,
                lastOutcome = premiumOutcome,
                isPurchasing = premiumPurchasing,
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
            com.uacastplayer.ui.epg.LocalEpgRefresh provides viewModel.epgController::refresh,
            LocalFeatureGate provides premiumUi.gate,
            LocalPremiumNotice provides premiumUi.notice,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ScaffoldZone(
                    viewModel = viewModel,
                    miniPlayerVisible = PlayerContainerStateMachine.isMiniPlayerVisible(
                        playerContainerState, hasRequest = playerRequest != null,
                    ),
                    playlistState = playlistState,
                    guidedTourDestination = guidedTourState.currentStep?.destination,
                    currentLanguage = currentLanguage,
                    currentAppTheme = currentAppTheme,
                    onExitApp = onFinish,
                    showHelp = showHelp,
                    showTerms = showTerms,
                    showPrivacyPolicy = showPrivacyPolicy,
                    showAddPlaylist = showAddPlaylist,
                    focusChannelsToken = focusChannelsToken,
                    onOpenHelp = { showHelp = true },
                    onOpenTerms = { showTerms = true },
                    onOpenPrivacyPolicy = { showPrivacyPolicy = true },
                    onBuildDiagnosticsReport = viewModel::buildDiagnosticsReport,
                    onOpenAddPlaylist = { showAddPlaylist = true },
                    onCloseHelp = { showHelp = false },
                    onCloseTerms = { showTerms = false },
                    onClosePrivacyPolicy = { showPrivacyPolicy = false },
                    onCloseAddPlaylist = { showAddPlaylist = false },
                    onChannelSelected = openPlayer,
                    onPlaylistLoaded = {
                        showAddPlaylist = false
                        focusChannelsToken++
                    },
                    // launchOrLogAbsence, not launch: a device with no Storage Access Framework - an
                    // Android TV box, a stripped ROM - throws out of these, on the main thread, from a tap.
                    pickPlaylistFile = {
                        pickPlaylistFile.launchOrLogAbsence(
                            arrayOf("audio/x-mpegurl", "*/*"),
                            "pick a playlist",
                        )
                    },
                    exportBackupFile = {
                        exportBackupFile.launchOrLogAbsence(
                            "ua-cast-backup-${LocalDate.now()}.json",
                            "export a backup",
                        )
                    },
                    importBackupFile = {
                        importBackupFile.launchOrLogAbsence(
                            arrayOf("application/json", "*/*"),
                            "import a backup",
                        )
                    },
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
                    backHandlerBlocked = showHelp || showTerms || showPrivacyPolicy || showAddPlaylist,
                )

                BatteryHintZone(viewModel = viewModel)

                // Wait until first-run guidance and the player are out of the way. The controller
                // spaces reminders by release tag and time; "Later" leaves the persistent banner.
                UpdateOfferOverlay(
                    viewModel = viewModel,
                    hasSeenGuidedTour = hasSeenGuidedTour,
                    guidedTourVisible = guidedTourState.isVisible,
                    playerRequest = playerRequest,
                    showHelp = showHelp,
                    showTerms = showTerms,
                    showPrivacyPolicy = showPrivacyPolicy,
                    showAddPlaylist = showAddPlaylist,
                    release = updateState.promptRelease,
                )

                // Last, so the unlock dialog and the premium sheet sit over the screen that raised them -
                // including over the player, which is itself an overlay.
                premiumUi.overlays()
            }
        }
    }
}

@Composable
private fun UpdateOfferOverlay(
    viewModel: AppViewModel,
    hasSeenGuidedTour: Boolean,
    guidedTourVisible: Boolean,
    playerRequest: PlayerRequest?,
    showHelp: Boolean,
    showTerms: Boolean,
    showPrivacyPolicy: Boolean,
    showAddPlaylist: Boolean,
    release: GitHubRelease?,
) {
    val blocked = when {
        !hasSeenGuidedTour -> true
        guidedTourVisible || playerRequest != null -> true
        showHelp || showTerms || showPrivacyPolicy -> true
        showAddPlaylist -> true
        else -> false
    }
    val apk = release?.apk
    if (!blocked && release != null && apk != null) {
        val uriHandler = LocalUriHandler.current
        UpdateOfferDialog(
            release = release,
            onInstall = {
                viewModel.acknowledgeUpdatePrompt()
                viewModel.downloadAndInstallUpdate(apk)
            },
            onLater = viewModel::acknowledgeUpdatePrompt,
            onOpenRelease = { url ->
                try {
                    uriHandler.openUri(url)
                } catch (e: IllegalArgumentException) {
                    AppLog.w("MainActivity") { "no app can open release notes: ${e.javaClass.simpleName}" }
                }
            },
        )
    }
}
