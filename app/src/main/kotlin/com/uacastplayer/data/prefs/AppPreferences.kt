package com.uacastplayer.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.i18n.LanguageResolver
import com.uacastplayer.epg.EpgSource

/**
 * Small, synchronous wrapper around the app's single general-settings [SharedPreferences] file.
 * Values here are all tiny scalars; there is no need for the AtomicFile/versioned-snapshot
 * machinery used by the playlist/EPG/favorites caches.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = LanguageResolver.fromStoredCode(prefs.getString(KEY_LANGUAGE, null))
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.code).apply()

    val hasChosenLanguage: Boolean
        get() = prefs.contains(KEY_LANGUAGE)

    var epgSource: EpgSource
        get() = EpgSource.fromId(prefs.getString(KEY_EPG_SOURCE, null))
        set(value) = prefs.edit().putString(KEY_EPG_SOURCE, value.id).apply()

    var iconWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_ICON_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_ICON_WIFI_ONLY, value).apply()

    var lastIconPrefetchAtMillis: Long?
        get() = if (prefs.contains(KEY_LAST_ICON_PREFETCH)) prefs.getLong(KEY_LAST_ICON_PREFETCH, 0L) else null
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_LAST_ICON_PREFETCH).apply()
            } else {
                prefs.edit().putLong(KEY_LAST_ICON_PREFETCH, value).apply()
            }
        }

    var iconDisplayMode: IconDisplayMode
        get() = IconDisplayMode.fromId(prefs.getString(KEY_ICON_DISPLAY_MODE, null))
        set(value) = prefs.edit().putString(KEY_ICON_DISPLAY_MODE, value.name).apply()

    var listDensity: ListDensity
        get() = ListDensity.fromId(prefs.getString(KEY_LIST_DENSITY, null))
        set(value) = prefs.edit().putString(KEY_LIST_DENSITY, value.name).apply()

    var channelLayout: ChannelLayout
        get() = ChannelLayout.fromId(prefs.getString(KEY_CHANNEL_LAYOUT, null))
        set(value) = prefs.edit().putString(KEY_CHANNEL_LAYOUT, value.name).apply()

    var wrapAroundEnabled: Boolean
        get() = prefs.getBoolean(KEY_WRAP_AROUND, true)
        set(value) = prefs.edit().putBoolean(KEY_WRAP_AROUND, value).apply()

    var autoSkipDeadEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SKIP_DEAD, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SKIP_DEAD, value).apply()

    private companion object {
        const val PREFS_NAME = "uacast_prefs"
        const val KEY_LANGUAGE = "language_code"
        const val KEY_EPG_SOURCE = "epg_source_id"
        const val KEY_ICON_WIFI_ONLY = "icon_wifi_only"
        const val KEY_LAST_ICON_PREFETCH = "last_icon_prefetch_at"
        const val KEY_ICON_DISPLAY_MODE = "icon_display_mode"
        const val KEY_LIST_DENSITY = "list_density"
        const val KEY_CHANNEL_LAYOUT = "channel_layout"
        const val KEY_WRAP_AROUND = "wrap_around_enabled"
        const val KEY_AUTO_SKIP_DEAD = "auto_skip_dead_enabled"
    }
}
