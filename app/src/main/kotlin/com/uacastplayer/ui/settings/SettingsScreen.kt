package com.uacastplayer.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.BuildConfig
import com.uacastplayer.R
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.data.prefs.ChannelLayout
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.data.prefs.ListDensity
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.performance.DeviceTier
import com.uacastplayer.settings.CacheKind
import com.uacastplayer.settings.SettingsUiState
import com.uacastplayer.ui.theme.AppIcons

@Composable
fun SettingsScreen(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    currentEpgSource: EpgSource,
    onEpgSourceSelected: (EpgSource) -> Unit,
    iconWifiOnly: Boolean,
    onIconWifiOnlyChanged: (Boolean) -> Unit,
    settingsState: SettingsUiState,
    onIconDisplayModeSelected: (IconDisplayMode) -> Unit,
    onListDensitySelected: (ListDensity) -> Unit,
    onChannelLayoutSelected: (ChannelLayout) -> Unit,
    onWrapAroundChanged: (Boolean) -> Unit,
    onAutoSkipChanged: (Boolean) -> Unit,
    onClearCache: (CacheKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSection(stringResource(R.string.settings_section_general), AppIcons.Globe) {
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
        }

        SettingsSection(stringResource(R.string.settings_section_playback), AppIcons.Play) {
            LabeledRow(stringResource(R.string.settings_icon_display_mode_label), AppIcons.Image) {
                for (mode in IconDisplayMode.entries) {
                    SettingsChip(
                        label = stringResource(mode.labelRes()),
                        isSelected = mode == settingsState.iconDisplayMode,
                        onClick = { onIconDisplayModeSelected(mode) },
                    )
                }
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
            SwitchRow(stringResource(R.string.settings_wrap_around_label), settingsState.wrapAroundEnabled, onWrapAroundChanged)
            SwitchRow(stringResource(R.string.settings_auto_skip_label), settingsState.autoSkipDeadEnabled, onAutoSkipChanged)
        }

        SettingsSection(stringResource(R.string.settings_section_cache), AppIcons.Storage) {
            CacheRow(R.string.cache_playlist_label, settingsState.cacheSizes.playlistBytes) { onClearCache(CacheKind.PLAYLIST) }
            CacheRow(R.string.cache_epg_label, settingsState.cacheSizes.epgBytes) { onClearCache(CacheKind.EPG) }
            CacheRow(R.string.cache_icons_label, settingsState.cacheSizes.iconCacheBytes) { onClearCache(CacheKind.ICONS) }
            CacheRow(R.string.cache_coil_label, settingsState.cacheSizes.coilCacheBytes) { onClearCache(CacheKind.COIL) }
        }

        SettingsSection(stringResource(R.string.settings_help), AppIcons.HelpCircle) {
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
        }
    }
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            content()
        }
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
