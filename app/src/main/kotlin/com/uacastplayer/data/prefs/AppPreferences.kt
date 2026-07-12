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

    private companion object {
        const val PREFS_NAME = "uacast_prefs"
        const val KEY_LANGUAGE = "language_code"
        const val KEY_EPG_SOURCE = "epg_source_id"
    }
}
