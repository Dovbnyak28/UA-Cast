package com.uacastplayer.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.settings.CacheKind
import com.uacastplayer.settings.IconSourceAddError
import com.uacastplayer.settings.SettingsUiState
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.Azure

@Composable
fun SettingsScreen(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    currentEpgSource: EpgSource,
    onEpgSourceSelected: (EpgSource) -> Unit,
    suggestedEpgUrl: String?,
    onUseSuggestedEpgUrl: () -> Unit,
    iconWifiOnly: Boolean,
    onIconWifiOnlyChanged: (Boolean) -> Unit,
    settingsState: SettingsUiState,
    playlistState: PlaylistUiState,
    onOpenAddPlaylist: () -> Unit,
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
    val context = LocalContext.current
    LaunchedEffect(backupImportSummary) {
        val summary = backupImportSummary ?: return@LaunchedEffect
        val text = context.getString(
            R.string.settings_data_import_summary,
            summary.importedSourceCount,
            summary.importedFavoriteCount,
        )
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        onDismissBackupImportSummary()
    }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsSection(title = stringResource(R.string.settings_section_general), icon = AppIcons.Globe) {
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_active_playlist_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = playlistState.displayName ?: playlistState.activePlaylistId ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            PlaylistActionRow(
                label = stringResource(R.string.home_add_playlist_button),
                icon = AppIcons.Plus,
                onClick = onOpenAddPlaylist,
                modifier = Modifier.padding(top = 12.dp),
            )
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            SwitchRow(stringResource(R.string.settings_icon_wifi_only_label), iconWifiOnly, onIconWifiOnlyChanged)
            LabeledRow(stringResource(R.string.settings_list_density_label), AppIcons.ViewList) {
                for (density in ListDensity.entries) {
                    SettingsChip(
                        label = stringResource(density.labelRes()),
                        isSelected = density == settingsState.listDensity,
                        onClick = { onListDensitySelected(density) },
                    )
                }
            }
            LabeledRow(stringResource(R.string.settings_channel_layout_label), AppIcons.GridView) {
                for (layout in ChannelLayout.entries) {
                    SettingsChip(
                        label = stringResource(layout.labelRes()),
                        icon = layout.icon(),
                        isSelected = layout == settingsState.channelLayout,
                        onClick = { onChannelLayoutSelected(layout) },
                    )
                }
            }
            LabeledRow(stringResource(R.string.settings_buffer_size_label), AppIcons.Storage) {
                for (size in BufferSize.entries) {
                    SettingsChip(
                        label = stringResource(size.labelRes()),
                        isSelected = size == settingsState.bufferSize,
                        onClick = { onBufferSizeSelected(size) },
                    )
                }
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onExportBackup, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_data_export))
                }
                OutlinedButton(onClick = onImportBackup, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_data_import))
                }
            }
        }

        SettingsSection(title = stringResource(R.string.settings_help), icon = AppIcons.HelpCircle) {
            Text(
                text = stringResource(R.string.settings_device_tier_label) + ": " + stringResource(settingsState.deviceTier.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_app_version) + ": " + BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.settings_help_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onClick) { Text(buttonLabel) }
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
                    .background(Azure.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Azure, modifier = Modifier.size(18.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        Icon(AppIcons.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).rotate(-90f))
    }
}

@Composable
private fun LabeledRow(label: String, icon: ImageVector, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onUse) { Text(stringResource(R.string.settings_epg_suggestion_action)) }
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.settings_battery_optimization_button)) }
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_icon_sources_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (addError != null) {
            Text(
                text = stringResource(addError.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onRemoveClick != null) {
            IconButton(onClick = onRemoveClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    AppIcons.Delete,
                    contentDescription = stringResource(R.string.settings_icon_sources_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatBytes(sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onClear) { Text(stringResource(R.string.cache_clear_button)) }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun SettingsChip(label: String, isSelected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val leadingIcon: (@Composable () -> Unit)? = when {
        isSelected -> { { Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } }
        icon != null -> { { Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } }
        else -> null
    }
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
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

private fun ChannelLayout.icon(): ImageVector = when (this) {
    ChannelLayout.LIST -> AppIcons.ViewList
    ChannelLayout.GRID -> AppIcons.GridView
    ChannelLayout.LARGE_ICONS -> AppIcons.LargeIcons
}

private fun DeviceTier.labelRes(): Int = when (this) {
    DeviceTier.LOW_END -> R.string.device_tier_low_end
    DeviceTier.MID_RANGE -> R.string.device_tier_mid_range
    DeviceTier.HIGH_END -> R.string.device_tier_high_end
}
