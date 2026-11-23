package app.zorik.player.ui.settings

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
import app.zorik.player.R
import app.zorik.player.core.i18n.AppLanguage
import app.zorik.player.epg.EpgUiState
import app.zorik.player.premium.Feature
import app.zorik.player.ui.components.SecondaryButton
import app.zorik.player.ui.premium.LocalFeatureGate
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.BodyRegular
import app.zorik.player.ui.theme.Caption
import app.zorik.player.ui.theme.UaTheme

@Composable
internal fun GeneralSettingsSection(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    currentAppTheme: AppTheme,
    onAppThemeSelected: (AppTheme) -> Unit,
) {
    val television = app.zorik.player.ui.tv.LocalTvMode.current
    val themes = app.zorik.player.ui.theme.availableAppThemes(television)
    val selectedTheme = app.zorik.player.ui.theme.themeForScreen(currentAppTheme, television)
    Column {
        SettingsChoiceRow(
                label = stringResource(R.string.settings_theme_label), icon = AppIcons.Image,
                options = themes.map { stringResource(it.nameRes()) },
                selectedIndex = themes.indexOf(selectedTheme),
                onSelected = { index -> onAppThemeSelected(themes[index]) },
            )
        SettingsSwitchCard(
            title = stringResource(R.string.settings_reduced_effects),
            description = stringResource(R.string.settings_reduced_effects_hint),
            icon = AppIcons.Brightness,
            checked = app.zorik.player.ui.theme.LocalReducedEffects.current,
            onCheckedChange = app.zorik.player.ui.theme.LocalReducedEffectsChange.current,
        )
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
    AppTheme.DAYLIGHT -> R.string.theme_name_daylight
    AppTheme.SYSTEM -> R.string.theme_name_system
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
