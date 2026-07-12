package com.uacastplayer

import android.app.Application
import android.content.Context
import androidx.core.os.LocaleListCompat
import com.uacastplayer.core.i18n.LanguageResolver
import com.uacastplayer.core.i18n.LocaleContextWrapper
import com.uacastplayer.data.prefs.AppPreferences

class UaCastPlayerApp : Application() {

    override fun attachBaseContext(base: Context) {
        val prefs = AppPreferences(base)
        val language = if (prefs.hasChosenLanguage) {
            prefs.language
        } else {
            LanguageResolver.fromDeviceLocales(systemLocaleTags(base))
        }
        super.attachBaseContext(LocaleContextWrapper.wrap(base, language))
    }

    private fun systemLocaleTags(context: Context): List<String> {
        val locales = LocaleListCompat.getAdjustedDefault()
        return (0 until locales.size()).mapNotNull { index ->
            locales[index]?.toLanguageTag()
        }
    }
}
