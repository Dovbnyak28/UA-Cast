package com.uacastplayer.data.prefs

import android.content.Context
import android.content.res.Configuration
import androidx.core.os.LocaleListCompat
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.i18n.LanguageResolver
import java.util.Locale

/**
 * The language the app should render in right now: the user's explicit choice, or (before they've
 * made one) the best supported match for the device's locales. This adapter lives beside
 * [AppPreferences], its state source, so the pure `core.i18n` package does not depend on data.
 */
fun Context.currentAppLanguage(): AppLanguage {
    val prefs = AppPreferences(this)
    if (prefs.hasChosenLanguage) return prefs.language
    val deviceLocales = LocaleListCompat.getAdjustedDefault()
    val tags = (0 until deviceLocales.size()).mapNotNull { deviceLocales[it]?.toLanguageTag() }
    return LanguageResolver.fromDeviceLocales(tags)
}

/**
 * This context wrapped so its resources resolve to [currentAppLanguage]. MainActivity is a
 * FragmentActivity, not an AppCompatActivity, so AppCompat never registers a locale delegate for
 * it. The Configuration is therefore wrapped directly from [AppPreferences], the source of truth.
 */
@Suppress("AppBundleLocaleChanges")
fun Context.withAppLocale(): Context {
    // Lint flags any Configuration.setLocale()/createConfigurationContext() pair as an app-bundle
    // language-split hazard. This only mirrors the locale the user already selected; it never
    // chooses or downloads a locale on its own.
    val locale = Locale.forLanguageTag(currentAppLanguage().code)
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    return createConfigurationContext(configuration)
}
