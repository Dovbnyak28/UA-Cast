package com.uacastplayer.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.premium.Feature
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.components.SegmentedControl
import com.uacastplayer.ui.premium.LocalFeatureGate
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.UaTheme

@Composable
internal fun GeneralSettingsSection(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    currentAppTheme: AppTheme,
    onAppThemeSelected: (AppTheme) -> Unit,
    currentEpgSource: EpgSource,
    onEpgSourceSelected: (EpgSource) -> Unit,
    suggestedEpgUrl: String?,
    epgTruncated: Boolean,
    onUseSuggestedEpgUrl: () -> Unit,
    onOpenBatteryOptimizationHint: () -> Unit,
) {
    val gate = LocalFeatureGate.current
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
        if (epgTruncated) EpgTruncatedRow()
        if (suggestedEpgUrl != null) {
            EpgSuggestionRow(gate.guard(Feature.CUSTOM_EPG_SOURCE, onUseSuggestedEpgUrl))
        }
        BatteryOptimizationRow(onOpenBatteryOptimizationHint)
    }
}

/** Internal so the screenshot test renders the theme picker from the production mapping. */
internal fun AppTheme.nameRes(): Int = when (this) {
    AppTheme.AZURE -> R.string.theme_name_azure
    AppTheme.CINEMA -> R.string.theme_name_cinema
    AppTheme.MIDNIGHT -> R.string.theme_name_midnight
}

private fun AppLanguage.nativeNameRes(): Int = when (this) {
    AppLanguage.UKRAINIAN -> R.string.language_name_uk
    AppLanguage.ENGLISH -> R.string.language_name_en
    AppLanguage.RUSSIAN -> R.string.language_name_ru
    AppLanguage.SPANISH -> R.string.language_name_es
}

private fun EpgSource.labelRes(): Int = when (this) {
    EpgSource.RECT_TRANSPARENT -> R.string.epg_source_rect_transparent
    EpgSource.SQUARE_DARK -> R.string.epg_source_square_dark
    EpgSource.PERFECT_PLAYER -> R.string.epg_source_perfect_player
    EpgSource.RECT_TRANSPARENT_SIMPLE -> R.string.epg_source_rect_transparent_simple
    EpgSource.SQUARE_DARK_SIMPLE -> R.string.epg_source_square_dark_simple
}

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
private fun EpgTruncatedRow() {
    Text(
        text = stringResource(R.string.settings_epg_truncated),
        style = Caption,
        color = UaTheme.palette.routeAmber,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
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
