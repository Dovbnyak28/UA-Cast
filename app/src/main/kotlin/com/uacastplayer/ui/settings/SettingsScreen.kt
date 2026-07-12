package com.uacastplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.BuildConfig
import com.uacastplayer.R
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.data.prefs.ChannelLayout
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.data.prefs.ListDensity
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.settings.CacheKind
import com.uacastplayer.settings.SettingsUiState

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
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionTitle(stringResource(R.string.settings_section_general))
        LabeledRow(stringResource(R.string.settings_language_label)) {
            AppLanguage.entries.forEach { language ->
                SettingsChip(
                    label = stringResource(language.nativeNameRes()),
                    isSelected = language == currentLanguage,
                    onClick = { onLanguageSelected(language) },
                )
            }
        }
        LabeledRow(stringResource(R.string.settings_epg_source_label)) {
            EpgSource.entries.forEachIndexed { index, source ->
                SettingsChip(
                    label = stringResource(R.string.settings_epg_source_variant, index + 1),
                    isSelected = source == currentEpgSource,
                    onClick = { onEpgSourceSelected(source) },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
        SectionTitle(stringResource(R.string.settings_section_playback))
        LabeledRow(stringResource(R.string.settings_icon_display_mode_label)) {
            IconDisplayMode.entries.forEach { mode ->
                SettingsChip(
                    label = stringResource(mode.labelRes()),
                    isSelected = mode == settingsState.iconDisplayMode,
                    onClick = { onIconDisplayModeSelected(mode) },
                )
            }
        }
        SwitchRow(stringResource(R.string.settings_icon_wifi_only_label), iconWifiOnly, onIconWifiOnlyChanged)
        LabeledRow(stringResource(R.string.settings_list_density_label)) {
            ListDensity.entries.forEach { density ->
                SettingsChip(
                    label = stringResource(density.labelRes()),
                    isSelected = density == settingsState.listDensity,
                    onClick = { onListDensitySelected(density) },
                )
            }
        }
        LabeledRow(stringResource(R.string.settings_channel_layout_label)) {
            ChannelLayout.entries.forEach { layout ->
                SettingsChip(
                    label = stringResource(layout.labelRes()),
                    isSelected = layout == settingsState.channelLayout,
                    onClick = { onChannelLayoutSelected(layout) },
                )
            }
        }
        SwitchRow(stringResource(R.string.settings_wrap_around_label), settingsState.wrapAroundEnabled, onWrapAroundChanged)
        SwitchRow(stringResource(R.string.settings_auto_skip_label), settingsState.autoSkipDeadEnabled, onAutoSkipChanged)

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
        SectionTitle(stringResource(R.string.settings_section_cache))
        CacheRow(R.string.cache_playlist_label, settingsState.cacheSizes.playlistBytes) { onClearCache(CacheKind.PLAYLIST) }
        CacheRow(R.string.cache_epg_label, settingsState.cacheSizes.epgBytes) { onClearCache(CacheKind.EPG) }
        CacheRow(R.string.cache_icons_label, settingsState.cacheSizes.iconCacheBytes) { onClearCache(CacheKind.ICONS) }
        CacheRow(R.string.cache_coil_label, settingsState.cacheSizes.coilCacheBytes) { onClearCache(CacheKind.COIL) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
        Text(
            text = stringResource(R.string.settings_app_version) + ": " + BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_help),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        Text(
            text = stringResource(R.string.settings_help_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
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
        Column(modifier = Modifier.weight(1f)) {
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
private fun SettingsChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
