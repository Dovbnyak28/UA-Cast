package com.uacastplayer.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.BuildConfig
import com.uacastplayer.R
import com.uacastplayer.backup.BackupExportResult
import com.uacastplayer.backup.BackupImportSummary
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.settings.BufferSize
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.core.settings.ListDensity
import com.uacastplayer.diagnostics.RemuxEffectivenessCounts
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.guidedtour.GuidedTourSectionState
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.settings.CacheKind
import com.uacastplayer.settings.SettingsUiState
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.update.UpdateSectionState

private enum class SettingsPage { OVERVIEW, GENERAL, PLAYLIST, PLAYBACK, DATA, SUPPORT }

@Composable
fun SettingsScreen(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    currentAppTheme: AppTheme,
    onAppThemeSelected: (AppTheme) -> Unit,
    currentEpgSource: EpgSource,
    onEpgSourceSelected: (EpgSource) -> Unit,
    suggestedEpgUrl: String?,
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
    backupExportResult: BackupExportResult?,
    onDismissBackupExportResult: () -> Unit,
    backupImportSummary: BackupImportSummary?,
    onDismissBackupImportSummary: () -> Unit,
    onOpenBatteryOptimizationHint: () -> Unit,
    onAddIconSource: (String) -> Unit,
    onRemoveIconSource: (String) -> Unit,
    onDismissIconSourceError: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenTerms: () -> Unit,
    onBuildDiagnosticsReport: () -> String,
    remuxEffectiveness: RemuxEffectivenessCounts,
    updateSection: UpdateSectionState,
    premiumSection: PremiumSectionState,
    guidedTourSection: GuidedTourSectionState,
    modifier: Modifier = Modifier,
) {
    var showBackupExportWarning by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(SettingsPage.OVERVIEW) }
    BackHandler(enabled = page != SettingsPage.OVERVIEW) { page = SettingsPage.OVERVIEW }
    if (showBackupExportWarning) {
        BackupExportWarningDialog(
            onConfirm = {
                showBackupExportWarning = false
                onExportBackup()
            },
            onDismiss = { showBackupExportWarning = false },
        )
    }

    key(page) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            backupExportResult?.let {
                BackupExportResultBanner(it, onDismissBackupExportResult)
            }
            backupImportSummary?.let {
                BackupImportSummaryBanner(it, onDismissBackupImportSummary)
            }

            if (page != SettingsPage.OVERVIEW) {
                PlaylistActionRow(
                    label = stringResource(R.string.settings_back_to_overview),
                    icon = AppIcons.ArrowBack,
                    onClick = { page = SettingsPage.OVERVIEW },
                )
            }

            when (page) {
                SettingsPage.OVERVIEW -> {
                    SettingsOverview(
                        generalSummary = stringResource(R.string.settings_page_general_summary) +
                            " · " +
                            stringResource(currentLanguage.settingsLabelRes()) +
                            " · " +
                            stringResource(currentAppTheme.settingsLabelRes()),
                        onOpenGeneral = { page = SettingsPage.GENERAL },
                        onOpenPlaylist = { page = SettingsPage.PLAYLIST },
                        onOpenPlayback = { page = SettingsPage.PLAYBACK },
                        onOpenData = { page = SettingsPage.DATA },
                        onOpenSupport = { page = SettingsPage.SUPPORT },
                    )
                }
                SettingsPage.GENERAL -> GeneralSettingsSection(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = onLanguageSelected,
                    currentAppTheme = currentAppTheme,
                    onAppThemeSelected = onAppThemeSelected,
                    currentEpgSource = currentEpgSource,
                    onEpgSourceSelected = onEpgSourceSelected,
                    suggestedEpgUrl = suggestedEpgUrl,
                    epgTruncated = epgTruncated,
                    onUseSuggestedEpgUrl = onUseSuggestedEpgUrl,
                    onOpenBatteryOptimizationHint = onOpenBatteryOptimizationHint,
                )
                SettingsPage.PLAYLIST -> {
                    PlaylistSettingsSection(
                        playlistState = playlistState,
                        hiddenGroupKeys = hiddenGroupKeys,
                        onOpenAddPlaylist = onOpenAddPlaylist,
                        onRestoreGroup = onRestoreGroup,
                    )
                    SettingsSection(
                        title = stringResource(R.string.settings_section_parental_control),
                        icon = AppIcons.Lock,
                    ) {
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
                }
                SettingsPage.PLAYBACK -> PlaybackSettingsSection(
                    settingsState = settingsState,
                    iconWifiOnly = iconWifiOnly,
                    displayActions = PlaybackDisplayActions(
                        onIconDisplayModeSelected,
                        onListDensitySelected,
                        onChannelLayoutSelected,
                        onBufferSizeSelected,
                    ),
                    behaviorActions = PlaybackBehaviorActions(
                        onIconWifiOnlyChanged,
                        onWrapAroundChanged,
                        onAutoSkipChanged,
                    ),
                    iconSourceActions = IconSourceActions(
                        onAddIconSource,
                        onRemoveIconSource,
                        onDismissIconSourceError,
                    ),
                )
                SettingsPage.DATA -> {
                    CacheSettingsSection(settingsState, onClearCache)
                    DataSettingsSection(
                        onImportBackup = onImportBackup,
                        onShowExportWarning = { showBackupExportWarning = true },
                    )
                }
                SettingsPage.SUPPORT -> {
                    PremiumSettingsSection(premiumSection)
                    if (BuildConfig.SELF_UPDATER_ENABLED) {
                        SettingsSection(
                            title = stringResource(R.string.settings_section_updates),
                            icon = AppIcons.Refresh,
                        ) {
                            UpdateCheckRow(updateSection)
                        }
                    }
                    TutorialSettingsSection(guidedTourSection)
                    HelpSettingsSection(
                        settingsState,
                        onOpenHelp,
                        onOpenTerms,
                        onBuildDiagnosticsReport,
                        remuxEffectiveness,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsOverview(
    onOpenGeneral: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenPlayback: () -> Unit,
    onOpenData: () -> Unit,
    onOpenSupport: () -> Unit,
    generalSummary: String = "",
) {
    SettingsNavigationRow(
        title = stringResource(R.string.settings_section_general),
        subtitle = generalSummary.ifBlank { stringResource(R.string.settings_page_general_summary) },
        icon = AppIcons.Settings,
        onClick = onOpenGeneral,
    )
    SettingsNavigationRow(
        title = stringResource(R.string.settings_page_playlist_access),
        subtitle = stringResource(R.string.settings_page_playlist_summary),
        icon = AppIcons.Channels,
        onClick = onOpenPlaylist,
    )
    SettingsNavigationRow(
        title = stringResource(R.string.settings_section_playback),
        subtitle = stringResource(R.string.settings_page_playback_summary),
        icon = AppIcons.Play,
        onClick = onOpenPlayback,
    )
    SettingsNavigationRow(
        title = stringResource(R.string.settings_page_data_storage),
        subtitle = stringResource(R.string.settings_page_data_summary),
        icon = AppIcons.Storage,
        onClick = onOpenData,
    )
    SettingsNavigationRow(
        title = stringResource(R.string.settings_page_help_about),
        subtitle = stringResource(R.string.settings_page_help_summary),
        icon = AppIcons.HelpCircle,
        onClick = onOpenSupport,
    )
}

private fun AppLanguage.settingsLabelRes(): Int = when (this) {
    AppLanguage.UKRAINIAN -> R.string.language_name_uk
    AppLanguage.ENGLISH -> R.string.language_name_en
    AppLanguage.RUSSIAN -> R.string.language_name_ru
    AppLanguage.SPANISH -> R.string.language_name_es
}

private fun AppTheme.settingsLabelRes(): Int = when (this) {
    AppTheme.AZURE -> R.string.theme_name_azure
    AppTheme.CINEMA -> R.string.theme_name_cinema
    AppTheme.MIDNIGHT -> R.string.theme_name_midnight
}
