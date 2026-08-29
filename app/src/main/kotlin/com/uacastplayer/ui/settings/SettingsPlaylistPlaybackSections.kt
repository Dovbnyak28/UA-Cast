package com.uacastplayer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.core.settings.BufferSize
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.core.settings.ListDensity
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.playlist.groupDisplayKey
import com.uacastplayer.premium.Feature
import com.uacastplayer.settings.CacheKind
import com.uacastplayer.settings.SettingsUiState
import com.uacastplayer.ui.components.SegmentedControl
import com.uacastplayer.ui.premium.LocalFeatureGate
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.CaptionSemibold
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.raisedSurface

internal data class PlaybackDisplayActions(
    val onIconDisplayModeSelected: (IconDisplayMode) -> Unit,
    val onListDensitySelected: (ListDensity) -> Unit,
    val onChannelLayoutSelected: (ChannelLayout) -> Unit,
    val onBufferSizeSelected: (BufferSize) -> Unit,
)

internal data class PlaybackBehaviorActions(
    val onIconWifiOnlyChanged: (Boolean) -> Unit,
    val onWrapAroundChanged: (Boolean) -> Unit,
    val onAutoSkipChanged: (Boolean) -> Unit,
)

internal data class IconSourceActions(
    val onAdd: (String) -> Unit,
    val onRemove: (String) -> Unit,
    val onDismissError: () -> Unit,
)

@Composable
internal fun PlaylistSettingsSection(
    playlistState: PlaylistUiState,
    hiddenGroupKeys: Set<String>,
    onOpenAddPlaylist: () -> Unit,
    onRestoreGroup: (String) -> Unit,
) {
    val gate = LocalFeatureGate.current
    SettingsSection(title = stringResource(R.string.settings_section_playlist), icon = AppIcons.Channels) {
        // An empty first install has no active source. Rendering the fallback name here used to
        // contradict Home and Channels, which correctly said there was no playlist at all.
        if (playlistState.hasChannels) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
        }
        val addPlaylist = if (playlistState.hasChannels) {
            gate.guard(Feature.MULTI_PLAYLIST, onOpenAddPlaylist)
        } else {
            onOpenAddPlaylist
        }
        PlaylistActionRow(
            label = stringResource(R.string.home_add_playlist_button),
            icon = AppIcons.Plus,
            onClick = addPlaylist,
            modifier = if (playlistState.hasChannels) Modifier.padding(top = 12.dp) else Modifier,
        )
        HiddenGroupsControl(playlistState, hiddenGroupKeys, onRestoreGroup)
    }
}

@Composable
private fun HiddenGroupsControl(
    playlistState: PlaylistUiState,
    hiddenGroupKeys: Set<String>,
    onRestoreGroup: (String) -> Unit,
) {
    if (hiddenGroupKeys.isEmpty()) return
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

@Composable
internal fun PlaybackSettingsSection(
    settingsState: SettingsUiState,
    iconWifiOnly: Boolean,
    displayActions: PlaybackDisplayActions,
    behaviorActions: PlaybackBehaviorActions,
    iconSourceActions: IconSourceActions,
) {
    val gate = LocalFeatureGate.current
    SettingsSection(title = stringResource(R.string.settings_section_playback), icon = AppIcons.Play) {
        SegmentedRow(stringResource(R.string.settings_detail_level_label), AppIcons.Image) {
            SegmentedControl(
                options = List(DETAIL_LEVEL_PRESETS.size) { stringResource(detailLevelLabelRes(it)) },
                selectedIndex = detailLevelIndex(settingsState.iconDisplayMode),
                onSelected = { index ->
                    val (mode, density) = DETAIL_LEVEL_PRESETS[index]
                    displayActions.onIconDisplayModeSelected(mode)
                    displayActions.onListDensitySelected(density)
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
        SwitchRow(
            stringResource(R.string.settings_icon_wifi_only_label),
            iconWifiOnly,
            behaviorActions.onIconWifiOnlyChanged,
        )
        SegmentedRow(stringResource(R.string.settings_channel_layout_label), AppIcons.GridView) {
            SegmentedControl(
                options = ChannelLayout.entries.map { stringResource(it.labelRes()) },
                selectedIndex = ChannelLayout.entries.indexOf(settingsState.channelLayout),
                onSelected = { displayActions.onChannelLayoutSelected(ChannelLayout.entries[it]) },
            )
        }
        SegmentedRow(stringResource(R.string.settings_buffer_size_label), AppIcons.Storage) {
            SegmentedControl(
                options = BufferSize.entries.map { stringResource(it.labelRes()) },
                selectedIndex = BufferSize.entries.indexOf(settingsState.bufferSize),
                onSelected = { displayActions.onBufferSizeSelected(BufferSize.entries[it]) },
            )
        }
        SwitchRow(
            stringResource(R.string.settings_wrap_around_label),
            settingsState.wrapAroundEnabled,
            behaviorActions.onWrapAroundChanged,
        )
        SwitchRow(
            stringResource(R.string.settings_auto_skip_label),
            settingsState.autoSkipDeadEnabled,
            behaviorActions.onAutoSkipChanged,
        )
        IconSourcesSection(
            customSources = settingsState.customIconSources,
            addError = settingsState.iconSourceAddError,
            onAddSource = { url -> gate.guard(Feature.CUSTOM_ICON_SOURCES) { iconSourceActions.onAdd(url) }() },
            onRemoveSource = iconSourceActions.onRemove,
            onDismissError = iconSourceActions.onDismissError,
        )
    }
}

@Composable
internal fun CacheSettingsSection(settingsState: SettingsUiState, onClearCache: (CacheKind) -> Unit) {
    SettingsSection(title = stringResource(R.string.settings_section_cache), icon = AppIcons.Storage) {
        CacheRow(R.string.cache_playlist_label, settingsState.cacheSizes.playlistBytes) {
            onClearCache(CacheKind.PLAYLIST)
        }
        CacheRow(R.string.cache_epg_label, settingsState.cacheSizes.epgBytes) {
            onClearCache(CacheKind.EPG)
        }
        CacheRow(R.string.cache_icons_label, settingsState.cacheSizes.iconCacheBytes) {
            onClearCache(CacheKind.ICONS)
        }
        CacheRow(R.string.cache_coil_label, settingsState.cacheSizes.coilCacheBytes) {
            onClearCache(CacheKind.COIL)
        }
    }
}

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
