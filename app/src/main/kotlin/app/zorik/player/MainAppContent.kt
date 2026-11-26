package app.zorik.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zorik.player.core.i18n.AppLanguage
import app.zorik.player.core.ui.findActivity
import app.zorik.player.ui.platform.launchOrLogAbsence
import app.zorik.player.ui.settings.LocalIconPackCheck
import app.zorik.player.ui.settings.rememberIconPackCheckAction
import app.zorik.player.favorites.FavoriteKey
import app.zorik.player.player.PlayerContainerStateMachine
import app.zorik.player.player.PlayerRequest
import app.zorik.player.parentalcontrol.PlayerChannelAccess
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.premium.PremiumSectionState
import app.zorik.player.ui.guidedtour.GuidedTipConditions
import app.zorik.player.ui.guidedtour.GuidedTourCallbacks
import app.zorik.player.ui.guidedtour.GuidedTourHost
import app.zorik.player.ui.nav.AdaptiveRootLayout
import app.zorik.player.ui.tv.LocalTvMode
import app.zorik.player.ads.BannerAdAudience
import app.zorik.player.ui.ads.LocalBannerAdAudience
import app.zorik.player.ui.components.RetainedUnderlay
import app.zorik.player.ui.premium.LocalFeatureGate
import app.zorik.player.ui.premium.LocalPremiumNotice
import app.zorik.player.ui.premium.rememberFeatureGate
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.player.PlayerRequestViewModel

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
    val backupDocuments = rememberBackupDocumentActions(viewModel)
    val iconPackCheck = rememberIconPackCheckAction(playlistState.channels)

    val requestOwner = androidx.lifecycle.viewmodel.compose.viewModel<PlayerRequestViewModel>()
    val playerRequest by requestOwner.request.collectAsStateWithLifecycle()
    var pendingOpen by remember { mutableStateOf<PlayerRequest?>(null) }
    var opaquePlayerToken by remember { mutableStateOf<Any?>(null) }
    var playerContainerState by rememberSaveable {
        mutableStateOf(PlayerContainerStateMachine.State.CLOSED)
    }
    app.zorik.player.ui.theme.SystemBarAppearance(
        dark = app.zorik.player.ui.theme.UaTheme.palette.isDark ||
            (playerRequest != null && playerContainerState == PlayerContainerStateMachine.State.EXPANDED),
    )
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
    val pendingGuidedTips by viewModel.pendingGuidedTips.collectAsStateWithLifecycle()

    // Offered here rather than from AppViewModel's init, so it happens *after* the language and
    // terms gates rather than behind them.
    val television = LocalTvMode.current
    LaunchedEffect(television) { if (!television) viewModel.offerGuidedTourOnLaunch() }
    // Incremented (never reset) each time a playlist load finishes from AddPlaylistScreen, so
    // RootScaffold's LaunchedEffect(token) fires again even if the value happened to repeat - it's
    // a one-shot "switch to Channels" signal, not a persisted tab selection.
    var focusChannelsToken by remember { mutableIntStateOf(0) }

    // After process death, playerRequest starts out null but savedPlayerRequest may still hold the
    // channel that was playing - re-open it once the restored playlist has loaded far enough to
    // find it by key. The original sub-list (e.g. a specific group or search results) isn't
    // recoverable, so this falls back to the full flat playlist; if the channel is gone entirely,
    // the saved marker is dropped and the player just stays closed.
    RestorePlayerRequest(
        savedPlayerRequest, playlistState, parentalReady, lockedKeys, sessionUnlocked, requestOwner,
        { savedPlayerRequest }, viewModel::persistedLastWatchedChannelKey,
        onRestored = {
            if (playerContainerState == PlayerContainerStateMachine.State.CLOSED) {
                playerContainerState = PlayerContainerStateMachine.reduce(
                    playerContainerState,
                    PlayerContainerStateMachine.Event.Open,
                )
            }
        },
        onMissing = { savedPlayerRequest = null },
    )

    val guidedTourCallbacks = remember(viewModel) {
        GuidedTourCallbacks(
            onIntroNext = viewModel::introNext,
            onIntroBack = viewModel::introBack,
            onIntroPageSettled = viewModel::goToIntroPage,
            onFinishIntro = viewModel::finishIntro,
            onAddPlaylist = {
                viewModel.finishIntro()
                showAddPlaylist = true
            },
            onShowTip = { tip -> viewModel.showGuidedTip(tip) },
            onDismissTip = viewModel::dismissGuidedTip,
        )
    }
    val tipConditions = GuidedTipConditions(
        pending = pendingGuidedTips,
        enabled = noScreenOverApp(showHelp, showTerms, showPrivacyPolicy, showAddPlaylist),
        playerExpanded = playerRequest != null && playerContainerState == PlayerContainerStateMachine.State.EXPANDED,
        supported = !television,
    )

    // Wraps everything, including the player and the dialogs: the intro and the tips are drawn over
    // the app, and the app is what the tips point at. Placed here rather than inside ScaffoldZone so
    // a target registered anywhere - the player's video and Cast buttons, the channel search -
    // reaches the same registry.
    app.zorik.player.ui.media.LocalVideoDocumentHost(onVideo = { video ->
        viewModel.interruptGuidedTour()
        showHelp = false
        showTerms = false
        showPrivacyPolicy = false
        showAddPlaylist = false
        val request = PlayerRequest.forLocalVideo(video)
        openPlayer(request.channels, request.startIndex)
    }) {
    GuidedTourHost(
        state = guidedTourState,
        callbacks = guidedTourCallbacks,
        tips = tipConditions,
        hasChannels = playlistState.channels.isNotEmpty(),
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
            LocalIconPackCheck provides iconPackCheck,
            // Stored paid access is resolved synchronously in AppViewModel's init before this UI.
            // Suppress all ad work during local/remote playback and first-run guidance.
            LocalBannerAdAudience provides BannerAdAudience(entitlements,
                blocked = playerRequest != null || guidedTourState.isVisible),
            app.zorik.player.ui.epg.LocalEpgRefresh provides viewModel.epgController::refresh,
            LocalFeatureGate provides premiumUi.gate,
            LocalPremiumNotice provides premiumUi.notice,
        ) {
            // Resolve the shared viewport before either layer is measured. A nested constraints
            // subcomposition in PlayerZone used to report opacity after navigation was measured.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val surfaceRequest = playerRequest
                // Keep only a small UI token after closure, never the previous channel list.
                val surfaceToken = remember(surfaceRequest) { Any() }
                RetainedUnderlay(
                    covered = {
                        surfaceRequest != null && playerContainerState == PlayerContainerStateMachine.State.EXPANDED &&
                            opaquePlayerToken === surfaceToken
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CompositionLocalProvider(app.zorik.player.ui.tv.LocalTvBrowseActive provides
                        (!television || playerContainerState != PlayerContainerStateMachine.State.EXPANDED)) {
                        ScaffoldZone(
                            viewModel = viewModel,
                            miniPlayerVisible = PlayerContainerStateMachine.isMiniPlayerVisible(
                                playerContainerState, hasRequest = playerRequest != null,
                            ),
                            playlistState = playlistState,
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
                            exportBackupFile = backupDocuments.export,
                            importBackupFile = backupDocuments.restore,
                            requireParentalControlUnlock = requireParentalControlUnlock,
                            premiumSection = premiumSection,
                        )
                    }
                }

                // A single stable PlayerHost call site, always composed whenever a channel is loaded
                // regardless of layout, drawn as a later sibling so it overlays whatever ScaffoldZone is
                // showing - this must never be duplicated into a second call site (e.g. one per branch),
                // since PlayerHost owns its own NavHost/PlayerViewModel/ExoPlayer and a second call site
                // would mean a second, independent instance of all three.
                PlayerZone(
                    viewModel = viewModel,
                    playerRequest = surfaceRequest,
                    playerContainerState = playerContainerState,
                    usesNavigationRail = maxWidth >= AdaptiveRootLayout.MEDIUM_WIDTH_DP.dp,
                    onPlayerContainerStateChange = { playerContainerState = it },
                    onClosePlayer = closePlayer,
                    onOpacityChanged = { opaque ->
                        if (opaque) opaquePlayerToken = surfaceToken
                        else if (opaquePlayerToken === surfaceToken) opaquePlayerToken = null
                    },
                )

                BatteryHintZone(viewModel = viewModel)

                // Last, so the unlock dialog and the premium sheet sit over the screen that raised them -
                // including over the player, which is itself an overlay.
                premiumUi.overlays()
            }
        }
    }
    }
}

/** True while none of the full-screen pages that sit over the app is open - the tips' "nobody is in
 * the middle of something" rule. */
private fun noScreenOverApp(vararg screensOpen: Boolean): Boolean = screensOpen.none { it }
