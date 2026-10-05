package com.uacastplayer

import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.appTheme

/** Refreshes Home after the separate PlayerViewModel writes the last-watched preference. */
internal fun AppViewModel.refreshLastWatchedChannel() {
    lastWatchedChannelKeyMutable.value = preferences.lastWatchedChannelKey
}

internal fun AppViewModel.reopenBatteryOptimizationHint() {
    showBatteryOptimizationHintMutable.value = true
}

internal fun AppViewModel.dismissBatteryOptimizationHint() {
    preferences.hasSeenBatteryOptimizationHint = true
    showBatteryOptimizationHintMutable.value = false
}

internal fun AppViewModel.selectLanguage(language: AppLanguage) {
    preferences.language = language
    uiStateMutable.value = uiStateMutable.value.copy(language = language, needsLanguagePicker = false)
}

internal fun AppViewModel.selectAppTheme(theme: AppTheme) {
    preferences.appTheme = theme
    uiStateMutable.value = uiStateMutable.value.copy(appTheme = theme)
}

/** Declining terms is handled at the Activity level and exits the app. */
internal fun AppViewModel.acceptTerms() {
    preferences.hasAcceptedTerms = true
    uiStateMutable.value = uiStateMutable.value.copy(needsTermsAcceptance = false)
}
