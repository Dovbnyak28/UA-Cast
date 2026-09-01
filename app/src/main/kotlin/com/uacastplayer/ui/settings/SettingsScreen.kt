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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.RadiusField
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.components.IconHeader
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.UiTestTags
import androidx.compose.ui.platform.testTag
import com.uacastplayer.update.UpdateSectionState

private enum class SettingsPage { OVERVIEW, GENERAL, PLAYLIST, PLAYBACK, DATA, SUPPORT }

private data class SettingsNavigationItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

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
    onOpenPrivacyPolicy: () -> Unit,
    onBuildDiagnosticsReport: () -> String,
    remuxEffectiveness: RemuxEffectivenessCounts,
    updateSection: UpdateSectionState,
    premiumSection: PremiumSectionState,
    guidedTourSection: GuidedTourSectionState,
    modifier: Modifier = Modifier,
) {
    var showBackupExportWarning by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(SettingsPage.OVERVIEW) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
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
            modifier = modifier.fillMaxWidth(),
        ) {
            if (page != SettingsPage.OVERVIEW) {
                SettingsSubpageHeader(
                    title = stringResource(settingsPageTitle(page)),
                    onBack = { page = SettingsPage.OVERVIEW },
                )
            }
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
                        onOpenGeneral = { page = SettingsPage.GENERAL },
                        onOpenPlaylist = { page = SettingsPage.PLAYLIST },
                        onOpenPlayback = { page = SettingsPage.PLAYBACK },
                        onOpenData = { page = SettingsPage.DATA },
                        onOpenSupport = { page = SettingsPage.SUPPORT },
                    )
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
                            onOpenPrivacyPolicy,
                            onBuildDiagnosticsReport,
                            remuxEffectiveness,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSubpageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = AppIcons.ArrowBack,
                contentDescription = stringResource(R.string.settings_back_to_overview),
                tint = UaTheme.palette.labelPrimary,
            )
        }
        Text(
            text = title,
            style = Title,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@StringRes
private fun settingsPageTitle(page: SettingsPage): Int = when (page) {
    SettingsPage.OVERVIEW -> R.string.nav_settings
    SettingsPage.GENERAL -> R.string.settings_section_general
    SettingsPage.PLAYLIST -> R.string.settings_page_playlist_access
    SettingsPage.PLAYBACK -> R.string.settings_section_playback
    SettingsPage.DATA -> R.string.settings_page_data_storage
    SettingsPage.SUPPORT -> R.string.settings_page_help_about
}

@Composable
internal fun SettingsOverview(
    onOpenGeneral: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenPlayback: () -> Unit,
    onOpenData: () -> Unit,
    onOpenSupport: () -> Unit,
    generalSummary: String = "",
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text(stringResource(R.string.settings_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = AppIcons.Search,
                contentDescription = null,
                tint = UaTheme.palette.labelSecondary,
            )
        },
        trailingIcon = if (searchQuery.isNotBlank()) {
            {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = stringResource(R.string.settings_search_clear),
                        tint = UaTheme.palette.labelSecondary,
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(RadiusField),
        colors = uaTextFieldColors(),
        modifier = Modifier.fillMaxWidth().testTag(UiTestTags.SETTINGS_SEARCH),
    )

    val items = listOf(
        SettingsNavigationItem(
            title = stringResource(R.string.settings_section_general),
            subtitle = generalSummary.ifBlank { stringResource(R.string.settings_page_general_summary) },
            icon = AppIcons.Settings,
            onClick = onOpenGeneral,
        ),
        SettingsNavigationItem(
            title = stringResource(R.string.settings_page_playlist_access),
            subtitle = stringResource(R.string.settings_page_playlist_summary),
            icon = AppIcons.Channels,
            onClick = onOpenPlaylist,
        ),
        SettingsNavigationItem(
            title = stringResource(R.string.settings_section_playback),
            subtitle = stringResource(R.string.settings_page_playback_summary),
            icon = AppIcons.Play,
            onClick = onOpenPlayback,
        ),
        SettingsNavigationItem(
            title = stringResource(R.string.settings_page_data_storage),
            subtitle = stringResource(R.string.settings_page_data_summary),
            icon = AppIcons.Storage,
            onClick = onOpenData,
        ),
        SettingsNavigationItem(
            title = stringResource(R.string.settings_page_help_about),
            subtitle = stringResource(R.string.settings_page_help_summary),
            icon = AppIcons.HelpCircle,
            onClick = onOpenSupport,
        ),
    )
    val normalizedQuery = searchQuery.trim()
    val filteredItems = if (normalizedQuery.isEmpty()) {
        items
    } else {
        items.filter { item ->
            item.title.contains(normalizedQuery, ignoreCase = true) ||
                item.subtitle.contains(normalizedQuery, ignoreCase = true)
        }
    }
    if (filteredItems.isEmpty()) {
        IconHeader(
            icon = AppIcons.Search,
            title = stringResource(R.string.settings_search_no_results, normalizedQuery),
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        filteredItems.forEach { item ->
            SettingsNavigationRow(
                title = item.title,
                subtitle = item.subtitle,
                icon = item.icon,
                onClick = item.onClick,
            )
        }
    }
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
