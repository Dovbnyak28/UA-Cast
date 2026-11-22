package app.zorik.player

import app.zorik.player.core.i18n.AppLanguage
import app.zorik.player.ui.theme.AppTheme

data class AppUiState(
    val language: AppLanguage = AppLanguage.DEFAULT,
    val appTheme: AppTheme = AppTheme.DEFAULT,
    val needsLanguagePicker: Boolean = true,
    val needsTermsAcceptance: Boolean = true,
)
