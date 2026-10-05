package com.uacastplayer.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.theme.UaTheme
import java.util.Locale

internal enum class SettingsPage { OVERVIEW, GENERAL, PLAYLIST, PLAYBACK, PARENTAL, DATA, SUPPORT }

/** Search resolves a real localized control, not just destination prose. */
internal data class SettingsSearchEntry(
    @StringRes val labelRes: Int,
    val title: String,
    val page: SettingsPage,
    val aliases: String = "",
) {
    fun matches(query: String): Boolean {
        val tokens = normalize(query).split(' ').filter(String::isNotEmpty)
        val haystack = normalize("$title $aliases")
        return tokens.isNotEmpty() && tokens.all(haystack::contains)
    }
}

private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
    .replace("wi-fi", "wifi").replace("wi fi", "wifi")

@Composable
internal fun settingsSearchEntries(): List<SettingsSearchEntry> = listOf(
    searchEntry(R.string.settings_theme_label, SettingsPage.GENERAL, ""),
    searchEntry(R.string.settings_language_label, SettingsPage.GENERAL, ""),
    searchEntry(R.string.settings_epg_source_label, SettingsPage.PLAYLIST, "EPG XMLTV"),
    searchEntry(R.string.settings_battery_optimization_label, SettingsPage.PLAYBACK, ""),
    searchEntry(R.string.settings_icon_wifi_only_label, SettingsPage.GENERAL, "wifi"),
    searchEntry(R.string.settings_detail_level_label, SettingsPage.GENERAL, ""),
    searchEntry(R.string.settings_channel_layout_label, SettingsPage.GENERAL, ""),
    searchEntry(R.string.settings_buffer_size_label, SettingsPage.PLAYBACK, ""),
    searchEntry(R.string.settings_wrap_around_label, SettingsPage.PLAYBACK, ""),
    searchEntry(R.string.settings_auto_skip_label, SettingsPage.PLAYBACK, ""),
    searchEntry(R.string.settings_icon_sources_title, SettingsPage.GENERAL, ""),
    searchEntry(R.string.home_add_playlist_button, SettingsPage.PLAYLIST, "M3U M3U8 Xtream"),
    searchEntry(R.string.settings_section_parental_control, SettingsPage.PARENTAL, "PIN"),
    searchEntry(R.string.settings_section_cache, SettingsPage.DATA, ""),
    searchEntry(R.string.settings_section_data, SettingsPage.DATA, "backup резерв копія копия copia export import"),
    searchEntry(R.string.settings_data_export, SettingsPage.DATA, "backup"),
    searchEntry(R.string.settings_data_import, SettingsPage.DATA, "backup"),
    searchEntry(R.string.settings_section_premium, SettingsPage.SUPPORT, ""),
    searchEntry(R.string.settings_section_tutorial, SettingsPage.SUPPORT, ""),
)

@Composable
private fun searchEntry(@StringRes label: Int, page: SettingsPage, aliases: String) =
    SettingsSearchEntry(label, stringResource(label), page, aliases)

internal val LocalSettingsSearchTarget = compositionLocalOf<String?> { null }

/** Scroll concerns stay in the UI; no preference or runtime state is copied here. */
@Composable
internal fun Modifier.settingsSearchTarget(label: String): Modifier {
    val target = LocalSettingsSearchTarget.current
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(target, label) {
        if (target == label) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    return bringIntoViewRequester(requester).then(
        if (target == label) Modifier.border(1.dp, UaTheme.palette.azure) else Modifier,
    )
}
