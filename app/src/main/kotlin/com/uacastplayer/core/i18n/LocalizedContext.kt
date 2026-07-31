package com.uacastplayer.core.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.core.os.LocaleListCompat
import com.uacastplayer.data.prefs.AppPreferences
import java.util.Locale

/**
 * The language the app should render in right now: the user's explicit choice, or (before they've
 * made one) the best supported match for the device's locales.
 */
fun Context.currentAppLanguage(): AppLanguage {
    val prefs = AppPreferences(this)
    if (prefs.hasChosenLanguage) return prefs.language
    val deviceLocales = LocaleListCompat.getAdjustedDefault()
    val tags = (0 until deviceLocales.size()).mapNotNull { deviceLocales[it]?.toLanguageTag() }
    return LanguageResolver.fromDeviceLocales(tags)
}

/**
 * [context] wrapped so its resources resolve to [currentAppLanguage]. MainActivity is a
 * FragmentActivity, not an AppCompatActivity (see its class doc), so AppCompat never registers a
 * delegate for it - which means AppCompatDelegate's per-app-language APIs silently no-op in this
 * app. This wraps the Configuration directly from our own AppPreferences instead, which is the
 * actual source of truth for the selected language.
 */
@Suppress("AppBundleLocaleChanges")
fun Context.withAppLocale(): Context {
    // Lint flags any Configuration.setLocale()/createConfigurationContext() pair as an app-bundle
    // language-split hazard, but this only mirrors the locale the user already picked (the actual
    // language switch, which is what that check cares about) - it never picks a locale on its own.
    val locale = Locale.forLanguageTag(currentAppLanguage().code)
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    return createConfigurationContext(configuration)
}
