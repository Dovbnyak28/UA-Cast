package com.uacastplayer.ui.theme

import com.uacastplayer.data.prefs.AppPreferences

/** UI-side mapping between the persisted stable ID and the visual theme model. Persistence owns
 * the string; the design system owns which themes exist and which one is the default. */
var AppPreferences.appTheme: AppTheme
    get() = AppTheme.fromId(appThemeId)
    set(value) {
        appThemeId = value.name
    }
