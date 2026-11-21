package com.uacastplayer.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.backup.BackupExportResult
import com.uacastplayer.backup.BackupImportSummary
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.settings.BufferSize
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.core.settings.ListDensity
import com.uacastplayer.diagnostics.RemuxEffectivenessCounts
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.guidedtour.GuidedTourSectionState
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.settings.CacheKind
import com.uacastplayer.settings.SettingsUiState
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.UaTheme


@Composable
fun SettingsScreen(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    currentAppTheme: AppTheme,
    onAppThemeSelected: (AppTheme) -> Unit,
    epgState: EpgUiState,
    onEpgUrlChanged: (String) -> Unit,
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
    onOpenPrivacyPolicy: () -> Unit,
    onBuildDiagnosticsReport: () -> String,
    remuxEffectiveness: RemuxEffectivenessCounts,
    premiumSection: PremiumSectionState,
    guidedTourSection: GuidedTourSectionState,
    modifier: Modifier = Modifier,
    openEpgSettings: Boolean = false,
    onEpgSettingsOpened: () -> Unit = {},
) {
    var showBackupExportWarning by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(SettingsPage.OVERVIEW) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchTarget by rememberSaveable { mutableStateOf<Int?>(null) }
    OpenEpgSettingsEffect(openEpgSettings, onEpgSettingsOpened) {
        page = SettingsPage.PLAYLIST
        searchTarget = R.string.home_epg_label
    }
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

    CompositionLocalProvider(LocalSettingsSearchTarget provides searchTarget?.let { stringResource(it) }) {
        key(page) {
            Column(
                modifier = modifier.fillMaxWidth(),
            ) {
                com.uacastplayer.ui.components.ScreenHeader(
                    title = stringResource(settingsPageTitle(page)),
                    onBack = if (page == SettingsPage.OVERVIEW) null else ({ page = SettingsPage.OVERVIEW }),
                )
                Column(
                    modifier = Modifier
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

                    when (page) {
                        SettingsPage.OVERVIEW -> SettingsOverview(
                            generalSummary = stringResource(R.string.settings_page_general_summary) +
                                " · " +
                                stringResource(currentLanguage.settingsLabelRes()) +
                                " · " +
                                stringResource(currentAppTheme.settingsLabelRes()),
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onOpenSetting = { entry -> searchTarget = entry.labelRes; page = entry.page },
                            onOpenGeneral = { page = SettingsPage.GENERAL },
                            onOpenPlaylist = { page = SettingsPage.PLAYLIST },
                            onOpenPlayback = { page = SettingsPage.PLAYBACK },
                            onOpenData = { page = SettingsPage.DATA },
                            onOpenSupport = { page = SettingsPage.SUPPORT },
                            onOpenParental = { page = SettingsPage.PARENTAL },
                        )
                        SettingsPage.GENERAL -> {
                            GeneralSettingsSection(
                                currentLanguage, onLanguageSelected, currentAppTheme, onAppThemeSelected,
                            )
                            DisplaySettingsSection(
                                settingsState = settingsState,
                                iconWifiOnly = iconWifiOnly,
                                displayActions = PlaybackDisplayActions(
                                    onIconDisplayModeSelected, onListDensitySelected,
                                    onChannelLayoutSelected, onIconWifiOnlyChanged,
                                ),
                                iconSourceActions = IconSourceActions(
                                    onAddIconSource, onRemoveIconSource, onDismissIconSourceError,
                                ),
                            )
                        }
                        SettingsPage.PLAYLIST -> {
                            PlaylistSettingsSection(
                                playlistState = playlistState,
                                hiddenGroupKeys = hiddenGroupKeys,
                                onOpenAddPlaylist = onOpenAddPlaylist,
                                onRestoreGroup = onRestoreGroup,
                            )
                            EpgSettingsSection(
                                epgState, onEpgUrlChanged, onUseSuggestedEpgUrl,
                            )
                        }
                        SettingsPage.PLAYBACK -> PlaybackSettingsSection(
                            settingsState = settingsState,
                            behaviorActions = PlaybackBehaviorActions(
                                onBufferSizeSelected, onWrapAroundChanged, onAutoSkipChanged,
                            ),
                            onOpenBatteryOptimizationHint = onOpenBatteryOptimizationHint,
                        )
                        SettingsPage.PARENTAL -> ParentalControlSection(
                            playlistState = playlistState,
                            lockedChannelKeys = lockedChannelKeys,
                            parentalControlPinSet = parentalControlPinSet,
                            onSetParentalControlPin = onSetParentalControlPin,
                            onResetParentalControl = onResetParentalControl,
                            onUnlockChannel = onUnlockChannel,
                            requireParentalControlUnlock = requireParentalControlUnlock,
                        )
                        SettingsPage.DATA -> {
                            CacheSettingsSection(settingsState, onClearCache)
                            DataSettingsSection(
                                onImportBackup = onImportBackup,
                                onShowExportWarning = { showBackupExportWarning = true },
                            )
                        }
                        SettingsPage.SUPPORT -> {
                            HelpSettingsSection(
                                settingsState,
                                onOpenHelp,
                                onOpenTerms,
                                onOpenPrivacyPolicy,
                                onBuildDiagnosticsReport,
                                remuxEffectiveness,
                            )
                            TutorialSettingsSection(guidedTourSection)
                            PremiumSettingsSection(premiumSection)
                        }
                    }
                }
            }
        }
    }
}

@StringRes
internal fun settingsPageTitle(page: SettingsPage): Int = when (page) {
    SettingsPage.OVERVIEW -> R.string.nav_settings
    SettingsPage.GENERAL -> R.string.settings_section_general
    SettingsPage.PLAYLIST -> R.string.settings_page_playlist_access
    SettingsPage.PLAYBACK -> R.string.settings_section_playback
    SettingsPage.PARENTAL -> R.string.settings_section_parental_control
    SettingsPage.DATA -> R.string.settings_page_data_storage
    SettingsPage.SUPPORT -> R.string.settings_page_help_about
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
    AppTheme.DAYLIGHT -> R.string.theme_name_daylight
    AppTheme.SYSTEM -> R.string.theme_name_system
}

@Composable
private fun OpenEpgSettingsEffect(requested: Boolean, onConsumed: () -> Unit, onOpen: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(requested) {
        if (requested) { onOpen(); onConsumed() }
    }
}
