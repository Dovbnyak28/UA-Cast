package com.uacastplayer.ui.settings
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.uacastplayer.BuildConfig
import androidx.compose.ui.platform.LocalContext
import com.uacastplayer.ui.diagnostics.DiagnosticsPreviewDialog
import com.uacastplayer.ui.diagnostics.sendDiagnostics
import com.uacastplayer.R
import com.uacastplayer.backup.BackupImportSummary
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.data.prefs.BufferSize
import com.uacastplayer.diagnostics.RemuxEffectivenessCounts
import com.uacastplayer.data.prefs.ChannelLayout
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.data.prefs.ListDensity
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.icons.IconResolver
import com.uacastplayer.performance.DeviceTier
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.playlist.groupDisplayKey
import com.uacastplayer.ui.channels.groupLabel
import com.uacastplayer.settings.CacheKind
import com.uacastplayer.settings.IconSourceAddError
import com.uacastplayer.settings.SettingsUiState
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.guidedtour.GuidedTourSectionState
import com.uacastplayer.premium.PremiumAvailability
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.ui.premium.PremiumContent
import com.uacastplayer.update.UpdateCheckOutcome
import com.uacastplayer.update.UpdateSectionState
import com.uacastplayer.ui.components.SegmentedControl
import com.uacastplayer.ui.components.SetPinDialog
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.theme.raisedSurface
import com.uacastplayer.premium.Feature
import com.uacastplayer.ui.premium.LocalFeatureGate
import com.uacastplayer.ui.premium.PremiumBadge
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.CaptionSemibold
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.Title
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    currentAppTheme: AppTheme,
    onAppThemeSelected: (AppTheme) -> Unit,
    currentEpgSource: EpgSource,
    onEpgSourceSelected: (EpgSource) -> Unit,
    suggestedEpgUrl: String?,
    /** The loaded guide hit [com.uacastplayer.epg.XmlTvParser]'s caps and was cut short - see
     * [com.uacastplayer.epg.EpgTruncation]. Shown here rather than on Home because the actionable
     * response is right above it: pick a smaller source. */
    epgTruncated: Boolean,
    onUseSuggestedEpgUrl: () -> Unit,
    iconWifiOnly: Boolean,
    onIconWifiOnlyChanged: (Boolean) -> Unit,
    settingsState: SettingsUiState,
    playlistState: PlaylistUiState,
    onOpenAddPlaylist: () -> Unit,
    hiddenGroupKeys: Set<String>,
    onRestoreGroup: (String) -> Unit,
    lockedChannelKeys: Set<String>,
    parentalControlPinSet: Boolean,
    onSetParentalControlPin: suspend (String) -> Boolean,
    onResetParentalControl: () -> Unit,
    onUnlockChannel: (M3uChannel) -> Unit,
    requireParentalControlUnlock: (() -> Unit) -> Unit,
    onIconDisplayModeSelected: (IconDisplayMode) -> Unit,
    onListDensitySelected: (ListDensity) -> Unit,
    onChannelLayoutSelected: (ChannelLayout) -> Unit,
    onBufferSizeSelected: (BufferSize) -> Unit,
    onWrapAroundChanged: (Boolean) -> Unit,
    onAutoSkipChanged: (Boolean) -> Unit,
    onClearCache: (CacheKind) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    backupImportSummary: BackupImportSummary?,
    onDismissBackupImportSummary: () -> Unit,
    onOpenBatteryOptimizationHint: () -> Unit,
    onAddIconSource: (String) -> Unit,
    onRemoveIconSource: (String) -> Unit,
    onDismissIconSourceError: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenTerms: () -> Unit,
    /** Builds the report the "send diagnostics" row previews. A function rather than a value: it
     * snapshots the log buffer and the crash record at the moment the user asks, not at whatever
     * moment this screen last recomposed. */
    onBuildDiagnosticsReport: () -> String,
    remuxEffectiveness: RemuxEffectivenessCounts,
    updateSection: UpdateSectionState,
    premiumSection: PremiumSectionState,
    guidedTourSection: GuidedTourSectionState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // One lookup for the whole screen: four of its sections offer something that is sold.
        val gate = LocalFeatureGate.current

        if (backupImportSummary != null) {
            BackupImportSummaryBanner(summary = backupImportSummary, onDismiss = onDismissBackupImportSummary)
        }

        SettingsSection(title = stringResource(R.string.settings_section_general), icon = AppIcons.Globe) {
            SegmentedRow(stringResource(R.string.settings_theme_label), AppIcons.Image) {
                SegmentedControl(
                    options = AppTheme.entries.map { stringResource(it.nameRes()) },
                    selectedIndex = AppTheme.entries.indexOf(currentAppTheme),
                    onSelected = { index -> onAppThemeSelected(AppTheme.entries[index]) },
                )
            }
            LabeledRow(stringResource(R.string.settings_language_label), AppIcons.Globe) {
                for (language in AppLanguage.entries) {
                    SettingsChip(
                        label = stringResource(language.nativeNameRes()),
                        isSelected = language == currentLanguage,
                        onClick = { onLanguageSelected(language) },
                    )
                }
            }
            LabeledRow(stringResource(R.string.settings_epg_source_label), AppIcons.Tv) {
                for (source in EpgSource.entries) {
                    SettingsChip(
                        label = stringResource(source.labelRes()),
                        isSelected = source == currentEpgSource,
                        onClick = { onEpgSourceSelected(source) },
                    )
                }
            }
            if (epgTruncated) {
                EpgTruncatedRow()
            }
            if (suggestedEpgUrl != null) {
                // The five built-in guides stay free; what is sold is following the URL the
                // *playlist* advertised, which is the only source here that is not on the list.
                EpgSuggestionRow(gate.guard(Feature.CUSTOM_EPG_SOURCE, onUseSuggestedEpgUrl))
            }
            BatteryOptimizationRow(onOpenBatteryOptimizationHint)
        }

        SettingsSection(title = stringResource(R.string.settings_section_playlist), icon = AppIcons.Channels) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Settings uses a plain verticalScroll Column, not a Lazy* list, so a shadow
                    // here doesn't re-trigger per-frame compositing on scroll - see
                    // docs/DESIGN_SYSTEM.md "§D Depth".
                    .raisedSurface(
                        RoundedCornerShape(RadiusItem),
                        UaTheme.palette.surface1,
                        edgeColor = UaTheme.palette.hairline,
                        shadow = true,
                    )
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_active_playlist_label),
                    style = CaptionSemibold,
                    color = UaTheme.palette.labelSecondary,
                )
                Text(
                    text = playlistState.displayName ?: stringResource(R.string.playlist_unnamed),
                    style = CardTitle,
                    color = UaTheme.palette.labelPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // The *first* playlist is never gated - an IPTV player with no playlist is not a
            // reduced app, it is a blank one, and nobody buys a blank one. What is sold is keeping
            // more than one, so the gate only applies once there is something to add to.
            val hasAPlaylistAlready = playlistState.hasChannels
            PlaylistActionRow(
                label = stringResource(R.string.home_add_playlist_button),
                icon = AppIcons.Plus,
                onClick = if (hasAPlaylistAlready) {
                    gate.guard(Feature.MULTI_PLAYLIST, onOpenAddPlaylist)
                } else {
                    onOpenAddPlaylist
                },
                modifier = Modifier.padding(top = 12.dp),
            )
            if (hiddenGroupKeys.isNotEmpty()) {
                var showHiddenGroups by rememberSaveable { mutableStateOf(false) }
                PlaylistActionRow(
                    label = stringResource(R.string.settings_hidden_groups, hiddenGroupKeys.size),
                    icon = AppIcons.Channels,
                    onClick = { showHiddenGroups = true },
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (showHiddenGroups) {
                    HiddenGroupsSheet(
                        groups = playlistState.groups.filter { groupDisplayKey(it.group) in hiddenGroupKeys },
                        onRestore = onRestoreGroup,
                        onDismiss = { showHiddenGroups = false },
                    )
                }
            }
        }

        SettingsSection(title = stringResource(R.string.settings_section_parental_control), icon = AppIcons.Lock) {
            ParentalControlSection(
                playlistState = playlistState,
                lockedChannelKeys = lockedChannelKeys,
                parentalControlPinSet = parentalControlPinSet,
                onSetParentalControlPin = onSetParentalControlPin,
                onResetParentalControl = onResetParentalControl,
                onUnlockChannel = onUnlockChannel,
                requireParentalControlUnlock = requireParentalControlUnlock,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_playback), icon = AppIcons.Play) {
            // A single combined preset instead of two separate three-way pickers (channel logos +
            // list density used to be their own rows) - both axes move together in lockstep with
            // DeviceTier anyway (see DeviceTierDefaults), so showing them as one control is a real
            // surface reduction with no behavior change: same three IconDisplayMode/ListDensity
            // values, same tier-default logic, just one row on screen instead of two.
            SegmentedRow(stringResource(R.string.settings_detail_level_label), AppIcons.Image) {
                SegmentedControl(
                    options = List(DETAIL_LEVEL_PRESETS.size) { stringResource(detailLevelLabelRes(it)) },
                    selectedIndex = detailLevelIndex(settingsState.iconDisplayMode),
                    onSelected = { index ->
                        val (mode, density) = DETAIL_LEVEL_PRESETS[index]
                        onIconDisplayModeSelected(mode)
                        onListDensitySelected(density)
                    },
                )
            }
            if (settingsState.iconDisplayModeIsAutomatic) {
                Text(
                    text = stringResource(R.string.settings_icon_display_mode_tier_default_hint),
                    style = Caption,
                    color = UaTheme.palette.labelSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            SwitchRow(stringResource(R.string.settings_icon_wifi_only_label), iconWifiOnly, onIconWifiOnlyChanged)
            SegmentedRow(stringResource(R.string.settings_channel_layout_label), AppIcons.GridView) {
                SegmentedControl(
                    options = ChannelLayout.entries.map { stringResource(it.labelRes()) },
                    selectedIndex = ChannelLayout.entries.indexOf(settingsState.channelLayout),
                    onSelected = { index -> onChannelLayoutSelected(ChannelLayout.entries[index]) },
                )
            }
            SegmentedRow(stringResource(R.string.settings_buffer_size_label), AppIcons.Storage) {
                SegmentedControl(
                    options = BufferSize.entries.map { stringResource(it.labelRes()) },
                    selectedIndex = BufferSize.entries.indexOf(settingsState.bufferSize),
                    onSelected = { index -> onBufferSizeSelected(BufferSize.entries[index]) },
                )
            }
            SwitchRow(stringResource(R.string.settings_wrap_around_label), settingsState.wrapAroundEnabled, onWrapAroundChanged)
            SwitchRow(stringResource(R.string.settings_auto_skip_label), settingsState.autoSkipDeadEnabled, onAutoSkipChanged)
            IconSourcesSection(
                customSources = settingsState.customIconSources,
                addError = settingsState.iconSourceAddError,
                onAddSource = { url -> gate.guard(Feature.CUSTOM_ICON_SOURCES) { onAddIconSource(url) }() },
                onRemoveSource = onRemoveIconSource,
                onDismissError = onDismissIconSourceError,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_cache), icon = AppIcons.Storage) {
            CacheRow(R.string.cache_playlist_label, settingsState.cacheSizes.playlistBytes) { onClearCache(CacheKind.PLAYLIST) }
            CacheRow(R.string.cache_epg_label, settingsState.cacheSizes.epgBytes) { onClearCache(CacheKind.EPG) }
            CacheRow(R.string.cache_icons_label, settingsState.cacheSizes.iconCacheBytes) { onClearCache(CacheKind.ICONS) }
            CacheRow(R.string.cache_coil_label, settingsState.cacheSizes.coilCacheBytes) { onClearCache(CacheKind.COIL) }
        }

        SettingsSection(
            title = stringResource(R.string.settings_section_data),
            icon = AppIcons.Upload,
            // The badge is on the section, not on each button: export and import are one capability
            // bought once, and two locks on two halves of it would read as two purchases.
            locked = gate.isLocked(Feature.BACKUP),
        ) {
            Text(
                text = stringResource(R.string.settings_data_hint),
                style = Caption,
                color = UaTheme.palette.labelSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Guarded rather than disabled. A greyed-out button says "this is broken"; a button
                // that answers with what it costs says what is actually true, and is the only way
                // the user finds out the feature exists at all.
                SecondaryButton(
                    text = stringResource(R.string.settings_data_export),
                    onClick = gate.guard(Feature.BACKUP, onExportBackup),
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = stringResource(R.string.settings_data_import),
                    onClick = gate.guard(Feature.BACKUP, onImportBackup),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        PremiumSettingsSection(premiumSection)

        SettingsSection(title = stringResource(R.string.settings_section_updates), icon = AppIcons.Refresh) {
            UpdateCheckRow(updateSection)
        }

        SettingsSection(title = stringResource(R.string.settings_section_tutorial), icon = AppIcons.HelpCircle) {
            Text(
                text = stringResource(
                    if (guidedTourSection.hasSeenTour) {
                        R.string.settings_tutorial_hint_seen
                    } else {
                        R.string.settings_tutorial_hint_unseen
                    },
                ),
                style = BodyRegular,
                color = UaTheme.palette.labelSecondary,
            )
            LinkRow(
                label = stringResource(R.string.settings_tutorial_label),
                buttonLabel = stringResource(R.string.settings_tutorial_button),
                onClick = guidedTourSection.onStartTour,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        SettingsSection(title = stringResource(R.string.settings_help), icon = AppIcons.HelpCircle) {
            Text(
                text = stringResource(R.string.settings_device_tier_label) + ": " + stringResource(settingsState.deviceTier.labelRes()),
                style = BodyRegular,
                color = UaTheme.palette.labelSecondary,
            )
            Text(
                text = stringResource(R.string.settings_app_version) + ": " + BuildConfig.VERSION_NAME,
                style = BodyRegular,
                color = UaTheme.palette.labelSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.settings_help_body),
                style = BodyRegular,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(top = 12.dp),
            )
            LinkRow(
                label = stringResource(R.string.settings_open_help),
                buttonLabel = stringResource(R.string.settings_open_button),
                onClick = onOpenHelp,
                modifier = Modifier.padding(top = 16.dp),
            )
            LinkRow(
                label = stringResource(R.string.settings_open_terms),
                buttonLabel = stringResource(R.string.settings_open_button),
                onClick = onOpenTerms,
                modifier = Modifier.padding(top = 8.dp),
            )
            SendDiagnosticsRow(
                onBuildReport = onBuildDiagnosticsReport,
                modifier = Modifier.padding(top = 8.dp),
            )
            RoutingEffectivenessBlock(remuxEffectiveness, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

/**
 * "Send diagnostics", beside Help because that is where somebody goes when something is wrong.
 *
 * The report is built on tap and shown in full before any mail app opens (see
 * [DiagnosticsPreviewDialog]). Nothing leaves the phone until the user presses send in their own
 * mail app, and the app itself opens no connection to anywhere - which is what lets it keep saying
 * it uploads nothing.
 */
@Composable
private fun SendDiagnosticsRow(onBuildReport: () -> String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    // Read through stringResource rather than off the Context: only this is tied to the
    // composition, so the in-app language switch re-reads it.
    val chooserTitle = stringResource(R.string.diagnostics_share_chooser_title)

    LinkRow(
        label = stringResource(R.string.settings_send_diagnostics),
        buttonLabel = stringResource(R.string.settings_send_button),
        onClick = { report = onBuildReport() },
        modifier = modifier,
    )

    report?.let { built ->
        DiagnosticsPreviewDialog(
            report = built,
            onCancel = { report = null },
            onSend = {
                report = null
                sendDiagnostics(context, built, chooserTitle)
            },
        )
    }
}

/**
 * The premium section, hidden until there is a store behind it - see [PremiumAvailability].
 *
 * `FakeBillingProvider` reports, truthfully, that this build has nothing for sale, so every price
 * is absent and the upgrade this section offers leads nowhere. The debug developer menu keeps the
 * section reachable in a debug build, which is where the license states are exercised; a release
 * build has neither branch, and R8 removes both.
 *
 * Its own composable rather than an `if` inline, so [SettingsScreen] stays under detekt's
 * complexity ceiling - that screen is a long list of sections and every conditional one costs it.
 */
@Composable
private fun PremiumSettingsSection(premiumSection: PremiumSectionState) {
    val hasDeveloperMenu = premiumSection.developerStates.isNotEmpty()
    if (!PremiumAvailability.STORE_IS_LIVE && !hasDeveloperMenu) return

    SettingsSection(title = stringResource(R.string.settings_section_premium), icon = AppIcons.Lock) {
        if (PremiumAvailability.STORE_IS_LIVE) {
            PremiumContent(section = premiumSection, nowMillis = System.currentTimeMillis())
        }
        if (hasDeveloperMenu) {
            LabeledRow(stringResource(R.string.settings_developer_license), AppIcons.Lock) {
                for (state in premiumSection.developerStates) {
                    SettingsChip(
                        label = state,
                        isSelected = false,
                        onClick = { premiumSection.onDeveloperStateSelected(state) },
                    )
                }
            }
        }
    }
}

private data class RouteLine(val labelRes: Int, val attempted: Int, val played: Int, val failed: Int)

/** Read-only, view-only local stats (see [RemuxEffectivenessStore]) - not sent anywhere, just a
 * "does the remux investment actually help" fact base for future work. */
@Composable
private fun RoutingEffectivenessBlock(counts: RemuxEffectivenessCounts, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_diagnostics_routing_label),
            style = CaptionSemibold,
            color = UaTheme.palette.labelSecondary,
        )
        val lines = listOf(
            RouteLine(
                R.string.settings_diagnostics_route_direct,
                counts.directAttempted,
                counts.directPlaying,
                counts.directFailed,
            ),
            RouteLine(
                R.string.settings_diagnostics_route_remux,
                counts.remuxAttempted,
                counts.remuxPlaying,
                counts.remuxFailed,
            ),
            RouteLine(
                R.string.settings_diagnostics_route_rewrite,
                counts.proxyRewriteAttempted,
                counts.proxyRewritePlaying,
                counts.proxyRewriteFailed,
            ),
        )
        for (line in lines) {
            Text(
                text = stringResource(
                    R.string.settings_diagnostics_route_line,
                    stringResource(line.labelRes),
                    line.attempted,
                    line.played,
                    line.failed,
                ),
                style = Caption,
                color = UaTheme.palette.labelSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun LinkRow(label: String, buttonLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.weight(1f),
        )
        SecondaryButton(text = buttonLabel, onClick = onClick)
    }
}

/**
 * Replaces the removed Toast for the backup-import summary - same dismissible banner language as
 * [com.uacastplayer.ui.components.DownloadStatusBanner]/[com.uacastplayer.ui.components.IconTierBanner]
 * (gradient card, hairline border, dismiss X), but inline content rather than a top overlay.
 */
@Composable
private fun BackupImportSummaryBanner(
    summary: BackupImportSummary,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(RadiusItem)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(listOf(UaTheme.palette.surface1, UaTheme.palette.surface2)),
                shape = shape,
            )
            .border(1.dp, UaTheme.palette.hairline, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.settings_data_import_summary,
                summary.importedSourceCount,
                summary.importedFavoriteCount,
            ),
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = stringResource(R.string.download_banner_dismiss),
                tint = UaTheme.palette.labelSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * "Check for updates", plus one line saying how that went.
 *
 * The result line is only ever written by a *manual* check (see
 * [com.uacastplayer.app.UpdateController]): the weekly automatic one is silent, so this row does
 * not report a failure the user never asked about. It is cleared on the way out, so returning to
 * Settings tomorrow does not show yesterday's answer as if it were fresh.
 */
@Composable
private fun UpdateCheckRow(section: UpdateSectionState) {
    DisposableEffect(Unit) { onDispose { section.onOutcomeShown() } }

    Text(
        text = stringResource(R.string.settings_update_hint),
        style = Caption,
        color = UaTheme.palette.labelSecondary,
    )

    if (section.state.isChecking) {
        // A spinner in place of the button, not a disabled button beside one: the button is the
        // only control here, and leaving it tappable is how six taps become six requests.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = UaTheme.palette.azure,
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(R.string.settings_update_checking),
                style = BodyRegular,
                color = UaTheme.palette.labelSecondary,
            )
        }
    } else {
        SecondaryButton(
            text = stringResource(R.string.settings_update_check_button),
            onClick = section.onCheckNow,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }

    val outcome = section.state.lastOutcome
    if (outcome != null && !section.state.isChecking) {
        val version = section.state.availableRelease?.tagName.orEmpty()
        Text(
            text = when (outcome) {
                UpdateCheckOutcome.UP_TO_DATE -> stringResource(R.string.settings_update_up_to_date)
                UpdateCheckOutcome.UPDATE_AVAILABLE -> stringResource(R.string.settings_update_available, version)
                UpdateCheckOutcome.FAILED -> stringResource(R.string.settings_update_failed)
            },
            style = BodyRegular,
            color = when (outcome) {
                UpdateCheckOutcome.FAILED -> UaTheme.palette.labelSecondary
                else -> UaTheme.palette.labelPrimary
            },
            modifier = Modifier.padding(top = 10.dp),
        )
        if (outcome == UpdateCheckOutcome.UPDATE_AVAILABLE) {
            val url = section.state.availableRelease?.releaseUrl
            if (url != null) {
                SecondaryButton(
                    text = stringResource(R.string.update_banner_action),
                    onClick = { section.onOpenRelease(url) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    /** Puts a [PremiumBadge] beside the title. Defaults to false so a section that sells nothing
     * needs to say nothing. */
    locked: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(UaTheme.palette.azure.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = UaTheme.palette.azure, modifier = Modifier.size(18.dp))
            }
            Text(
                text = title,
                style = CardTitle,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            if (locked) PremiumBadge()
        }
        Column(modifier = Modifier.padding(top = 16.dp)) { content() }
    }
}

@Composable
private fun PlaylistActionRow(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .raisedSurface(
                RoundedCornerShape(RadiusItem),
                UaTheme.palette.surface1,
                edgeColor = UaTheme.palette.hairline,
                shadow = true,
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = UaTheme.palette.labelSecondary, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        Icon(
            AppIcons.ChevronDown,
            contentDescription = null,
            tint = UaTheme.palette.labelSecondary,
            modifier = Modifier.size(16.dp).rotate(-90f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenGroupsSheet(groups: List<GroupedChannels>, onRestore: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(
                text = stringResource(R.string.settings_hidden_groups, groups.size),
                style = Title,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            for (grouped in groups) {
                val key = groupDisplayKey(grouped.group)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = groupLabel(grouped.group),
                        style = BodyRegular,
                        color = UaTheme.palette.labelPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.settings_hidden_groups_restore),
                        onClick = { onRestore(key) },
                    )
                }
            }
        }
    }
}

/** Set-up / manage / reset rows for the parental-control PIN lock - see
 * `app/ParentalControlController`'s doc for the locking/unlocking asymmetry these rows follow:
 * setting the first PIN needs no gate, but managing locked channels or changing the PIN both go
 * through [requireParentalControlUnlock], while [onResetParentalControl] (guarded only by its own
 * confirmation dialog below) deliberately doesn't. */
@Composable
private fun ParentalControlSection(
    playlistState: PlaylistUiState,
    lockedChannelKeys: Set<String>,
    parentalControlPinSet: Boolean,
    onSetParentalControlPin: suspend (String) -> Boolean,
    onResetParentalControl: () -> Unit,
    onUnlockChannel: (M3uChannel) -> Unit,
    requireParentalControlUnlock: (() -> Unit) -> Unit,
) {
    // Setting a PIN hashes it with PBKDF2 off the main thread, so the dialogs below submit into a
    // coroutine rather than reading the result inline.
    val pinScope = rememberCoroutineScope()
    if (!parentalControlPinSet) {
        var showSetPin by rememberSaveable { mutableStateOf(false) }
        // Only setting one up is gated. Everything below this branch - changing the PIN, unlocking
        // a channel, turning the whole thing off - runs for a user who already has one, whatever
        // their license says afterwards. A paywall that appears between a parent and a lock they
        // already configured is not a paywall, it is a hostage.
        PlaylistActionRow(
            label = stringResource(R.string.parental_control_set_up),
            icon = AppIcons.Lock,
            onClick = LocalFeatureGate.current.guard(Feature.PARENTAL_CONTROL) { showSetPin = true },
        )
        if (showSetPin) {
            SetPinDialog(
                onSubmit = { pin -> pinScope.launch { if (onSetParentalControlPin(pin)) showSetPin = false } },
                onDismiss = { showSetPin = false },
            )
        }
        return
    }

    var showManageLocked by rememberSaveable { mutableStateOf(false) }
    var showChangePin by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }

    PlaylistActionRow(
        label = stringResource(R.string.parental_control_manage_locked, lockedChannelKeys.size),
        icon = AppIcons.Lock,
        onClick = { requireParentalControlUnlock { showManageLocked = true } },
    )
    PlaylistActionRow(
        label = stringResource(R.string.parental_control_change_pin),
        icon = AppIcons.Lock,
        onClick = { requireParentalControlUnlock { showChangePin = true } },
        modifier = Modifier.padding(top = 8.dp),
    )
    PlaylistActionRow(
        label = stringResource(R.string.parental_control_reset),
        icon = AppIcons.Delete,
        onClick = { showResetConfirm = true },
        modifier = Modifier.padding(top = 8.dp),
    )

    if (showManageLocked) {
        val lockedChannels = remember(playlistState.groups, lockedChannelKeys) {
            playlistState.groups.flatMap { it.channels }.filter { FavoriteKey.of(it) in lockedChannelKeys }
        }
        LockedChannelsSheet(
            channels = lockedChannels,
            onUnlock = onUnlockChannel,
            onDismiss = { showManageLocked = false },
        )
    }
    if (showChangePin) {
        SetPinDialog(
            onSubmit = { pin -> pinScope.launch { if (onSetParentalControlPin(pin)) showChangePin = false } },
            onDismiss = { showChangePin = false },
        )
    }
    if (showResetConfirm) {
        ResetParentalControlConfirmDialog(
            onConfirm = { onResetParentalControl(); showResetConfirm = false },
            onDismiss = { showResetConfirm = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockedChannelsSheet(channels: List<M3uChannel>, onUnlock: (M3uChannel) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(
                text = stringResource(R.string.parental_control_manage_locked, channels.size),
                style = Title,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            for (channel in channels) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = channel.displayName,
                        style = BodyRegular,
                        color = UaTheme.palette.labelPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.parental_control_unlock_action),
                        onClick = { onUnlock(channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResetParentalControlConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UaTheme.palette.surface2,
        titleContentColor = UaTheme.palette.labelPrimary,
        textContentColor = UaTheme.palette.labelSecondary,
        title = { Text(stringResource(R.string.parental_control_reset_confirm_title)) },
        text = { Text(stringResource(R.string.parental_control_reset_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.parental_control_reset), color = UaTheme.palette.routeRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun LabeledRow(label: String, icon: ImageVector, content: @Composable () -> Unit) {
    RowLabel(label, icon)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Same label chrome as [LabeledRow], but for a single full-width [SegmentedControl] instead of a
 * scrolling chip row. */
@Composable
private fun SegmentedRow(label: String, icon: ImageVector, content: @Composable () -> Unit) {
    RowLabel(label, icon)
    content()
}

@Composable
private fun RowLabel(label: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        Icon(
            icon,
            contentDescription = null,
            tint = UaTheme.palette.labelSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = UaTheme.palette.labelPrimary,
                checkedTrackColor = UaTheme.palette.azure,
                checkedBorderColor = UaTheme.palette.azure,
                uncheckedThumbColor = UaTheme.palette.labelSecondary,
                uncheckedTrackColor = UaTheme.palette.surface2,
                uncheckedBorderColor = UaTheme.palette.hairline,
            ),
        )
    }
}

/** "EPG address found in playlist" hint (see EpgSourceAutoDetect.Action.Suggest) - only shown
 * when the user already picked an EPG source manually, so a found URL isn't silently applied. */
@Composable
private fun EpgSuggestionRow(onUse: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_epg_suggestion_hint),
            style = Caption,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.weight(1f),
        )
        SecondaryButton(text = stringResource(R.string.settings_epg_suggestion_action), onClick = onUse)
    }
}

/** Warns that the guide was cut off at the parser's cap. Deliberately plain text rather than a
 * dismissible banner: it is a standing property of the chosen source, not an event, so it should
 * stay visible for as long as that source is selected. */
@Composable
private fun EpgTruncatedRow() {
    Text(
        text = stringResource(R.string.settings_epg_truncated),
        style = Caption,
        // routeAmber, not amberGlow: the glow is the same hue at ~50% alpha and was never a text
        // color - see the doc on UaPalette.azureGlow.
        color = UaTheme.palette.routeAmber,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun BatteryOptimizationRow(onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_battery_optimization_label),
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.weight(1f),
        )
        SecondaryButton(text = stringResource(R.string.settings_battery_optimization_button), onClick = onOpen)
    }
}

/**
 * Lets the user add their own base-URL icon sources (tried before the built-in CDN fallback - see
 * [IconResolver]) and remove ones they added. The built-in source is shown for context but isn't
 * removable.
 */
@Composable
private fun IconSourcesSection(
    customSources: List<String>,
    addError: IconSourceAddError?,
    onAddSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var newSourceUrl by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = stringResource(R.string.settings_icon_sources_title),
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
        )
        Text(
            text = stringResource(R.string.settings_icon_sources_hint),
            style = Caption,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        IconSourceRow(
            urlText = stringResource(
                R.string.settings_icon_sources_builtin,
                IconResolver.BUILT_IN_ICON_SOURCE_BASE_URL,
            ),
            onRemoveClick = null,
        )
        customSources.forEach { source ->
            IconSourceRow(urlText = source, onRemoveClick = { onRemoveSource(source) })
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newSourceUrl,
                onValueChange = {
                    newSourceUrl = it
                    if (addError != null) onDismissError()
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.settings_icon_sources_placeholder)) },
                singleLine = true,
                isError = addError != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    capitalization = KeyboardCapitalization.None,
                ),
                colors = uaTextFieldColors(),
            )
            IconButton(onClick = {
                if (newSourceUrl.isNotBlank()) {
                    onAddSource(newSourceUrl)
                    newSourceUrl = ""
                }
            }) {
                Icon(
                    AppIcons.Plus,
                    contentDescription = stringResource(R.string.settings_icon_sources_add),
                    tint = UaTheme.palette.azure,
                )
            }
        }
        if (addError != null) {
            Text(
                text = stringResource(addError.messageRes()),
                style = Caption,
                color = UaTheme.palette.routeRed,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun IconSourceRow(urlText: String, onRemoveClick: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = urlText,
            style = Caption,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.weight(1f),
        )
        if (onRemoveClick != null) {
            IconButton(onClick = onRemoveClick) {
                Icon(
                    AppIcons.Delete,
                    contentDescription = stringResource(R.string.settings_icon_sources_remove),
                    tint = UaTheme.palette.labelSecondary,
                )
            }
        }
    }
}

private fun IconSourceAddError.messageRes(): Int = when (this) {
    IconSourceAddError.INVALID_URL -> R.string.settings_icon_sources_error_invalid
    IconSourceAddError.ALREADY_ADDED -> R.string.settings_icon_sources_error_duplicate
}

@Composable
private fun CacheRow(labelRes: Int, sizeBytes: Long, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            AppIcons.Storage,
            contentDescription = null,
            tint = UaTheme.palette.labelSecondary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = stringResource(labelRes),
                style = BodyRegular,
                color = UaTheme.palette.labelPrimary,
            )
            Text(
                text = formatBytes(sizeBytes),
                style = Caption,
                color = UaTheme.palette.labelSecondary,
            )
        }
        SecondaryButton(text = stringResource(R.string.cache_clear_button), onClick = onClear)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(Locale.ROOT, bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(Locale.ROOT, bytes / 1024.0)
    else -> "$bytes B"
}

/** See `docs/DESIGN_SYSTEM.md` "SettingsChip" - for option rows too numerous/long for [SegmentedControl]. */
@Composable
private fun SettingsChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(RadiusItem))
            .background(if (isSelected) UaTheme.palette.azure else UaTheme.palette.surface2)
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isSelected) {
            // Decorative: the row's own selected-state semantics (above) already announce
            // selection, and the color/text-weight change also conveys it visually.
            Icon(
                AppIcons.Check,
                contentDescription = null,
                tint = UaTheme.palette.labelPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = BodyRegular,
            color = if (isSelected) UaTheme.palette.labelPrimary else UaTheme.palette.labelSecondary,
        )
    }
}

/** Internal rather than private so `ThemePickerScreenshotTest` renders the picker off the same
 * mapping the screen does - a golden built on a copy of this `when` would not notice it drifting. */
internal fun AppTheme.nameRes(): Int = when (this) {
    AppTheme.AZURE -> R.string.theme_name_azure
    AppTheme.CINEMA -> R.string.theme_name_cinema
    AppTheme.MIDNIGHT -> R.string.theme_name_midnight
}

private fun AppLanguage.nativeNameRes(): Int = when (this) {
    AppLanguage.UKRAINIAN -> R.string.language_name_uk
    AppLanguage.ENGLISH -> R.string.language_name_en
    AppLanguage.RUSSIAN -> R.string.language_name_ru
    AppLanguage.SPANISH -> R.string.language_name_es
}

/** The combined "detail level" preset shown in Settings - index 0/1/2 pairs up an
 * [IconDisplayMode] with the [ListDensity] DeviceTierDefaults already assigns it for the same
 * device tier (LOW_END/MID_RANGE/HIGH_END), so picking a preset can never produce a combination
 * the automatic tiering wouldn't have picked on its own. */
private val DETAIL_LEVEL_PRESETS: List<Pair<IconDisplayMode, ListDensity>> = listOf(
    IconDisplayMode.CACHE to ListDensity.FULL,
    IconDisplayMode.CACHE_LIMITED to ListDensity.SIMPLE,
    IconDisplayMode.PLACEHOLDERS to ListDensity.MINIMAL,
)

private fun detailLevelIndex(iconDisplayMode: IconDisplayMode): Int = when (iconDisplayMode) {
    IconDisplayMode.CACHE -> 0
    IconDisplayMode.CACHE_LIMITED -> 1
    IconDisplayMode.PLACEHOLDERS -> 2
}

private fun detailLevelLabelRes(index: Int): Int = when (index) {
    0 -> R.string.settings_detail_level_full
    1 -> R.string.settings_detail_level_balanced
    else -> R.string.settings_detail_level_data_saver
}

private fun ChannelLayout.labelRes(): Int = when (this) {
    ChannelLayout.LIST -> R.string.channel_layout_list
    ChannelLayout.GRID -> R.string.channel_layout_grid
    ChannelLayout.LARGE_ICONS -> R.string.channel_layout_large_icons
}

private fun BufferSize.labelRes(): Int = when (this) {
    BufferSize.SMALL -> R.string.buffer_size_small
    BufferSize.MEDIUM -> R.string.buffer_size_medium
    BufferSize.LARGE -> R.string.buffer_size_large
}

private fun EpgSource.labelRes(): Int = when (this) {
    EpgSource.RECT_TRANSPARENT -> R.string.epg_source_rect_transparent
    EpgSource.SQUARE_DARK -> R.string.epg_source_square_dark
    EpgSource.PERFECT_PLAYER -> R.string.epg_source_perfect_player
    EpgSource.RECT_TRANSPARENT_SIMPLE -> R.string.epg_source_rect_transparent_simple
    EpgSource.SQUARE_DARK_SIMPLE -> R.string.epg_source_square_dark_simple
}

private fun DeviceTier.labelRes(): Int = when (this) {
    DeviceTier.LOW_END -> R.string.device_tier_low_end
    DeviceTier.MID_RANGE -> R.string.device_tier_mid_range
    DeviceTier.HIGH_END -> R.string.device_tier_high_end
}
