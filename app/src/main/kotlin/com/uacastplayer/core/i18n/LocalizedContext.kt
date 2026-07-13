package com.uacastplayer.core.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/**
 * [context] wrapped with the app's current per-app locale (from
 * [AppCompatDelegate.getApplicationLocales]). AppCompat's per-app language mechanism only
 * auto-applies to Activities below API 33 (via an internal lifecycle-callback hook); non-Activity
 * callers like [com.uacastplayer.cast.CastProxyService] that read string resources need to wrap
 * their own context explicitly to stay locale-correct on those older API levels.
 */
@Suppress("AppBundleLocaleChanges")
fun Context.withAppLocale(): Context {
    // Lint flags any Configuration.setLocale()/createConfigurationContext() pair as an app-bundle
    // language-split hazard, but this only mirrors the locale AppCompatDelegate already applied
    // (the actual language switch, which is what that check cares about) - it never picks a
    // locale on its own.
    val locale = AppCompatDelegate.getApplicationLocales().get(0) ?: return this
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    return createConfigurationContext(configuration)
}
