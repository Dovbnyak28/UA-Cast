package com.uacastplayer.ui.settings
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.uacastplayer.BuildConfig
import com.uacastplayer.R
import com.uacastplayer.backup.BackupImportSummary
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.data.prefs.BufferSize
import com.uacastplayer.data.prefs.ChannelLayout
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.data.prefs.ListDensity
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.icons.IconResolver
import com.uacastplayer.performance.DeviceTier
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.playlist.groupDisplayKey
import com.uacastplayer.ui.channels.groupLabel
import com.uacastplayer.settings.CacheKind
import com.uacastplayer.settings.IconSourceAddError
import com.uacastplayer.settings.SettingsUiState
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.components.SegmentedControl
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.theme.raisedSurface
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.CaptionSemibold
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.Title

@Composable
fun SettingsScreen(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    currentAppTheme: AppTheme,
    onAppThemeSelected: (AppTheme) -> Unit,
    currentEpgSource: EpgSource,
    onEpgSourceSelected: (EpgSource) -> Unit,
    suggestedEpgUrl: String?,
    onUseSuggestedEpgUrl: () -> Unit,
    iconWifiOnly: Boolean,
    onIconWifiOnlyChanged: (Boolean) -> Unit,
    settingsState: SettingsUiState,
    playlistState: PlaylistUiState,
    onOpenAddPlaylist: () -> Unit,
    hiddenGroupKeys: Set<String>,
    onRestoreGroup: (String) -> Unit,
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
            if (suggestedEpgUrl != null) {
                EpgSuggestionRow(onUseSuggestedEpgUrl)
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
                    text = playlistState.displayName ?: playlistState.activePlaylistId ?: "—",
                    style = CardTitle,
                    color = UaTheme.palette.labelPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            PlaylistActionRow(
                label = stringResource(R.string.home_add_playlist_button),
                icon = AppIcons.Plus,
                onClick = onOpenAddPlaylist,
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

        SettingsSection(title = stringResource(R.string.settings_section_playback), icon = AppIcons.Play) {
            LabeledRow(stringResource(R.string.settings_icon_display_mode_label), AppIcons.Image) {
                for (mode in IconDisplayMode.entries) {
                    SettingsChip(
                        label = stringResource(mode.labelRes()),
                        isSelected = mode == settingsState.iconDisplayMode,
                        onClick = { onIconDisplayModeSelected(mode) },
                    )
                }
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
            SegmentedRow(stringResource(R.string.settings_list_density_label), AppIcons.ViewList) {
                SegmentedControl(
                    options = ListDensity.entries.map { stringResource(it.labelRes()) },
                    selectedIndex = ListDensity.entries.indexOf(settingsState.listDensity),
                    onSelected = { index -> onListDensitySelected(ListDensity.entries[index]) },
                )
            }
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
                onAddSource = onAddIconSource,
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

        SettingsSection(title = stringResource(R.string.settings_section_data), icon = AppIcons.Upload) {
            Text(
                text = stringResource(R.string.settings_data_hint),
                style = Caption,
                color = UaTheme.palette.labelSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SecondaryButton(
                    text = stringResource(R.string.settings_data_export),
                    onClick = onExportBackup,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = stringResource(R.string.settings_data_import),
                    onClick = onImportBackup,
                    modifier = Modifier.weight(1f),
                )
            }
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
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.download_banner_dismiss),
                tint = UaTheme.palette.labelSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
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
            IconButton(onClick = onRemoveClick, modifier = Modifier.size(32.dp)) {
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
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isSelected) {
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

private fun AppTheme.nameRes(): Int = when (this) {
    AppTheme.AZURE -> R.string.theme_name_azure
    AppTheme.CINEMA -> R.string.theme_name_cinema
}

private fun AppLanguage.nativeNameRes(): Int = when (this) {
    AppLanguage.UKRAINIAN -> R.string.language_name_uk
    AppLanguage.ENGLISH -> R.string.language_name_en
    AppLanguage.RUSSIAN -> R.string.language_name_ru
    AppLanguage.SPANISH -> R.string.language_name_es
}

private fun IconDisplayMode.labelRes(): Int = when (this) {
    IconDisplayMode.PLACEHOLDERS -> R.string.icon_display_mode_placeholders
    IconDisplayMode.CACHE -> R.string.icon_display_mode_cache
    IconDisplayMode.CACHE_LIMITED -> R.string.icon_display_mode_cache_limited
}

private fun ListDensity.labelRes(): Int = when (this) {
    ListDensity.FULL -> R.string.list_density_full
    ListDensity.SIMPLE -> R.string.list_density_simple
    ListDensity.MINIMAL -> R.string.list_density_minimal
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
