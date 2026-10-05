package com.uacastplayer.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.uacastplayer.R
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.components.IconHeader
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.RadiusField
import com.uacastplayer.ui.theme.UaTheme

private data class SettingsNavigationItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

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
    onOpenSetting: ((SettingsSearchEntry) -> Unit)? = null,
    onOpenParental: () -> Unit = {},
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
            title = stringResource(R.string.settings_section_parental_control),
            subtitle = stringResource(R.string.settings_page_parental_summary),
            icon = AppIcons.Lock,
            onClick = onOpenParental,
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
    val pageActions = mapOf(
        SettingsPage.GENERAL to onOpenGeneral,
        SettingsPage.PLAYLIST to onOpenPlaylist,
        SettingsPage.PLAYBACK to onOpenPlayback,
        SettingsPage.PARENTAL to onOpenParental,
        SettingsPage.DATA to onOpenData,
        SettingsPage.SUPPORT to onOpenSupport,
    )
    val matchingSettings = settingsSearchEntries().filter { it.matches(normalizedQuery) }
    val filteredItems = if (normalizedQuery.isEmpty()) {
        items
    } else {
        items.filter { item ->
            item.title.contains(normalizedQuery, ignoreCase = true) ||
                item.subtitle.contains(normalizedQuery, ignoreCase = true)
        }
    }
    if (normalizedQuery.isNotEmpty()) {
        matchingSettings.forEach { entry ->
            SettingsNavigationRow(
                title = entry.title,
                subtitle = stringResource(settingsPageTitle(entry.page)),
                icon = AppIcons.Settings,
                onClick = { onOpenSetting?.invoke(entry) ?: pageActions[entry.page]?.invoke() },
            )
        }
    }
    if (filteredItems.isEmpty() && matchingSettings.isEmpty()) {
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
