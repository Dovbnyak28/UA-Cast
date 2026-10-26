package com.uacastplayer.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
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
import com.uacastplayer.epg.EpgUiState
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
) {
    Column {
        SegmentedRow(stringResource(R.string.settings_theme_label), AppIcons.Image) {
            SegmentedControl(
                options = AppTheme.entries.map { stringResource(it.nameRes()) },
                selectedIndex = AppTheme.entries.indexOf(currentAppTheme),
                onSelected = { index -> onAppThemeSelected(AppTheme.entries[index]) },
            )
        }
        SettingsChoiceRow(
            label = stringResource(R.string.settings_language_label),
            icon = AppIcons.Globe,
            options = AppLanguage.entries.map { stringResource(it.nativeNameRes()) },
            selectedIndex = AppLanguage.entries.indexOf(currentLanguage),
            onSelected = { onLanguageSelected(AppLanguage.entries[it]) },
        )
    }
}

@Composable
internal fun EpgSettingsSection(
    state: EpgUiState,
    onEpgUrlChanged: (String) -> Unit,
    onUseSuggestedEpgUrl: () -> Unit,
) {
    val gate = LocalFeatureGate.current
    SettingsSection(title = stringResource(R.string.home_epg_label), icon = AppIcons.Tv) {
        EpgSourceEditor(state.customUrl, state.hasError, onEpgUrlChanged)
        EpgSettingsStatus(state)
        Text(
            stringResource(R.string.settings_epg_user_source_hint),
            style = Caption,
            color = UaTheme.palette.labelSecondary,
        )
        if (state.data?.truncation?.any == true) EpgTruncatedRow()
        if (state.suggestedUrl != null) {
            EpgSuggestionRow(gate.guard(Feature.CUSTOM_EPG_SOURCE, onUseSuggestedEpgUrl))
        }
    }
}

@Composable
private fun EpgSettingsStatus(state: EpgUiState) {
    val message = when {
        state.isLoading -> R.string.epg_guide_loading
        state.hasError -> R.string.epg_guide_error
        state.data != null -> R.string.home_epg_ready
        else -> R.string.epg_guide_no_data
    }
    Text(stringResource(message), style = Caption, color = UaTheme.palette.labelSecondary)
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
internal fun BatteryOptimizationRow(onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .settingsSearchTarget(stringResource(R.string.settings_battery_optimization_label)).padding(top = 12.dp),
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
