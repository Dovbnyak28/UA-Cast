package com.uacastplayer

import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.ui.theme.AppTheme

data class AppUiState(
    val language: AppLanguage = AppLanguage.DEFAULT,
    val appTheme: AppTheme = AppTheme.DEFAULT,
    val needsLanguagePicker: Boolean = true,
    val needsTermsAcceptance: Boolean = true,
)
